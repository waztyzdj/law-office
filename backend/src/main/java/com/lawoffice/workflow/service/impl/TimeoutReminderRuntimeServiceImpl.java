package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.lawoffice.framework.config.TenantContextHolder;
import com.lawoffice.message.constant.MessageConstants;
import com.lawoffice.message.req.MessageActionReq;
import com.lawoffice.message.req.SendMessageReq;
import com.lawoffice.message.service.IMessageService;
import com.lawoffice.message.vo.MessageSendResultVO;
import com.lawoffice.system.entity.Tenant;
import com.lawoffice.system.mapper.TenantMapper;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.ProcessNodeConfig;
import com.lawoffice.workflow.entity.ReminderRecord;
import com.lawoffice.workflow.entity.Task;
import com.lawoffice.workflow.entity.TaskCandidate;
import com.lawoffice.workflow.mapper.ProcessInstanceMapper;
import com.lawoffice.workflow.mapper.ProcessNodeConfigMapper;
import com.lawoffice.workflow.mapper.ReminderRecordMapper;
import com.lawoffice.workflow.mapper.TaskCandidateMapper;
import com.lawoffice.workflow.mapper.TaskMapper;
import com.lawoffice.workflow.service.ITimeoutReminderRuntimeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class TimeoutReminderRuntimeServiceImpl implements ITimeoutReminderRuntimeService {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();
    private static final int DEFAULT_SCAN_LIMIT = 200;
    private static final int DEFAULT_INTERVAL_MINUTES = 60;
    private static final int DEFAULT_MAX_REMIND_COUNT = 3;
    private static final String CHANNEL_SITE = "site";
    private static final String SYSTEM_OPERATOR = "system";

    private final IMessageService messageService;
    private final ProcessInstanceMapper processInstanceMapper;
    private final ProcessNodeConfigMapper processNodeConfigMapper;
    private final ReminderRecordMapper reminderRecordMapper;
    private final TaskCandidateMapper taskCandidateMapper;
    private final TaskMapper taskMapper;
    private final TenantMapper tenantMapper;

    public TimeoutReminderRuntimeServiceImpl(IMessageService messageService,
            ProcessInstanceMapper processInstanceMapper,
            ProcessNodeConfigMapper processNodeConfigMapper,
            ReminderRecordMapper reminderRecordMapper,
            TaskCandidateMapper taskCandidateMapper,
            TaskMapper taskMapper,
            TenantMapper tenantMapper) {
        this.messageService = messageService;
        this.processInstanceMapper = processInstanceMapper;
        this.processNodeConfigMapper = processNodeConfigMapper;
        this.reminderRecordMapper = reminderRecordMapper;
        this.taskCandidateMapper = taskCandidateMapper;
        this.taskMapper = taskMapper;
        this.tenantMapper = tenantMapper;
    }

    @Override
    public int scanAndRemind() {
        int total = 0;
        for (Tenant tenant : listEnabledTenants()) {
            String previousTenantId = TenantContextHolder.getCurrentTenantId();
            try {
                TenantContextHolder.setCurrentTenantId(tenant.getId());
                total += scanTenant(tenant.getId());
            } catch (Exception e) {
                log.warn("Workflow timeout reminder scan failed, tenantId={}", tenant.getId(), e);
            } finally {
                if (StringUtils.hasText(previousTenantId)) {
                    TenantContextHolder.setCurrentTenantId(previousTenantId);
                } else {
                    TenantContextHolder.clear();
                }
            }
        }
        return total;
    }

    private List<Tenant> listEnabledTenants() {
        return tenantMapper.selectList(new QueryWrapper<Tenant>()
                .eq("status", 1)
                .eq("delete_flag", 0));
    }

    protected int scanTenant(String tenantId) {
        LocalDateTime now = LocalDateTime.now();
        List<Task> tasks = taskMapper.selectList(new QueryWrapper<Task>()
                .eq("tenant_id", tenantId)
                .eq("status", WorkflowConstants.Status.TODO)
                .isNotNull("due_time")
                .le("due_time", now)
                .eq("delete_flag", 0)
                .orderByAsc("due_time")
                .last("limit " + DEFAULT_SCAN_LIMIT));
        int count = 0;
        for (Task task : tasks) {
            count += remindTaskIfNeeded(task, tenantId, now);
        }
        return count;
    }

    private int remindTaskIfNeeded(Task task, String tenantId, LocalDateTime now) {
        ProcessInstance processInstance = processInstanceMapper.selectOne(new QueryWrapper<ProcessInstance>()
                .eq("tenant_id", tenantId)
                .eq("id", task.getProcessInstanceId())
                .eq("status", WorkflowConstants.Status.RUNNING)
                .eq("delete_flag", 0)
                .last("limit 1"));
        if (processInstance == null) {
            return 0;
        }
        TimeoutConfig config = resolveTimeoutConfig(processInstance, task, tenantId);
        if (!config.enabled() || config.timeoutMinutes() <= 0 || !config.supportsSiteChannel()) {
            return 0;
        }
        if (task.getDueTime() == null || task.getDueTime().isAfter(now)) {
            return 0;
        }
        int currentCount = task.getRemindCount() == null ? 0 : task.getRemindCount();
        if (currentCount >= config.maxRemindCount()) {
            return 0;
        }
        if (task.getLastRemindTime() != null
                && task.getLastRemindTime().plusMinutes(config.intervalMinutes()).isAfter(now)) {
            return 0;
        }

        List<ReminderTarget> targets = resolveTargets(task, tenantId);
        if (targets.isEmpty()) {
            return 0;
        }
        int nextRound = currentCount + 1;
        int inserted = 0;
        for (ReminderTarget target : targets) {
            if (existsRoundRecord(task.getId(), target.userId(), nextRound, tenantId)) {
                continue;
            }
            ReminderRecord record = createReminderRecord(processInstance, task, target, nextRound, tenantId, now);
            sendTimeoutMessage(processInstance, task, record);
            inserted++;
        }
        if (inserted > 0) {
            updateTaskReminderState(task, nextRound, tenantId, now);
        }
        return inserted;
    }

    private TimeoutConfig resolveTimeoutConfig(ProcessInstance processInstance, Task task, String tenantId) {
        ProcessNodeConfig nodeConfig = processNodeConfigMapper.selectOne(new QueryWrapper<ProcessNodeConfig>()
                .eq("tenant_id", tenantId)
                .eq("process_model_id", processInstance.getProcessModelId())
                .eq("node_id", task.getNodeId())
                .eq("delete_flag", 0)
                .last("limit 1"));
        if (nodeConfig == null || !StringUtils.hasText(nodeConfig.getTimeoutJson())) {
            return TimeoutConfig.disabled();
        }
        return TimeoutConfig.parse(nodeConfig.getTimeoutJson());
    }

    private List<ReminderTarget> resolveTargets(Task task, String tenantId) {
        Map<String, ReminderTarget> targets = new LinkedHashMap<>();
        if (StringUtils.hasText(task.getAssigneeUserId())) {
            targets.put(task.getAssigneeUserId(), new ReminderTarget(
                    task.getAssigneeUserId(), task.getAssigneeUsername(), task.getAssigneeRealname()));
            return new ArrayList<>(targets.values());
        }
        taskCandidateMapper.selectList(new QueryWrapper<TaskCandidate>()
                        .eq("tenant_id", tenantId)
                        .eq("task_id", task.getId())
                        .eq("status", WorkflowConstants.Status.ACTIVE)
                        .eq("delete_flag", 0))
                .forEach(candidate -> {
                    if (StringUtils.hasText(candidate.getCandidateUserId())) {
                        targets.putIfAbsent(candidate.getCandidateUserId(), new ReminderTarget(
                                candidate.getCandidateUserId(),
                                candidate.getCandidateUsername(),
                                candidate.getCandidateRealname()));
                    }
                });
        return new ArrayList<>(targets.values());
    }

    private boolean existsRoundRecord(String taskId, String receiverUserId, int round, String tenantId) {
        Long count = reminderRecordMapper.selectCount(new QueryWrapper<ReminderRecord>()
                .eq("tenant_id", tenantId)
                .eq("task_id", taskId)
                .eq("receiver_user_id", receiverUserId)
                .eq("remind_type", WorkflowConstants.RemindType.TIMEOUT)
                .eq("remind_round", round)
                .eq("delete_flag", 0));
        return count != null && count > 0;
    }

    private ReminderRecord createReminderRecord(ProcessInstance processInstance, Task task, ReminderTarget target,
            int round, String tenantId, LocalDateTime now) {
        ReminderRecord record = new ReminderRecord();
        record.setId(newId());
        record.setTenantId(tenantId);
        record.setProcessInstanceId(processInstance.getId());
        record.setTaskId(task.getId());
        record.setFlowableTaskId(task.getFlowableTaskId());
        record.setRemindType(WorkflowConstants.RemindType.TIMEOUT);
        record.setSenderUsername(SYSTEM_OPERATOR);
        record.setSenderRealname("系统");
        record.setReceiverUserId(target.userId());
        record.setReceiverUsername(target.username());
        record.setReceiverRealname(target.realname());
        record.setRemindRound(round);
        record.setOperateTime(now);
        record.setRemark("当前审批任务已超时，请尽快处理。");
        fillSystemAudit(record, tenantId, now);
        reminderRecordMapper.insert(record);
        return record;
    }

    private void sendTimeoutMessage(ProcessInstance processInstance, Task task, ReminderRecord record) {
        try {
            SendMessageReq req = new SendMessageReq();
            req.setTitle("审批超时提醒：" + processInstance.getInstanceTitle());
            req.setContent("当前审批任务“" + task.getTaskName() + "”已超时，请尽快处理。");
            req.setContentType(MessageConstants.CONTENT_TYPE_TEXT);
            req.setMessageType(MessageConstants.MESSAGE_TYPE_NOTICE);
            req.setPriority(MessageConstants.PRIORITY_IMPORTANT);
            req.setReceiverIds(List.of(record.getReceiverUserId()));
            req.setActions(List.of(buildTodoMessageAction(processInstance, task)));
            String operator = StringUtils.hasText(processInstance.getStarterUsername())
                    ? processInstance.getStarterUsername()
                    : record.getReceiverUsername();
            MessageSendResultVO result = messageService.sendMessage(req, operator);
            if (result != null && StringUtils.hasText(result.getMessageId())) {
                record.setMessageId(result.getMessageId());
                reminderRecordMapper.updateById(record);
            }
        } catch (Exception e) {
            log.warn("Workflow timeout message send failed, instanceId={}, taskId={}, receiverUserId={}",
                    processInstance.getId(), task.getId(), record.getReceiverUserId(), e);
        }
    }

    private MessageActionReq buildTodoMessageAction(ProcessInstance processInstance, Task task) {
        MessageActionReq action = new MessageActionReq();
        action.setActionType(MessageConstants.ACTION_TYPE_INTERNAL_ROUTE);
        action.setActionName("办理审批");
        action.setRoutePath("/workflow/todo");
        action.setRouteQuery("{\"instanceId\":\"" + processInstance.getId()
                + "\",\"taskId\":\"" + task.getId() + "\"}");
        action.setBizType("workflow_timeout");
        action.setBizId(processInstance.getId());
        action.setOpenType(MessageConstants.OPEN_TYPE_CURRENT);
        action.setSortOrder(1);
        return action;
    }

    private void updateTaskReminderState(Task task, int nextRound, String tenantId, LocalDateTime now) {
        taskMapper.update(null, new UpdateWrapper<Task>()
                .eq("tenant_id", tenantId)
                .eq("id", task.getId())
                .eq("status", WorkflowConstants.Status.TODO)
                .eq("delete_flag", 0)
                .set("last_remind_time", now)
                .set("remind_count", nextRound)
                .set("update_by", SYSTEM_OPERATOR)
                .set("update_time", now));
    }

    private void fillSystemAudit(ReminderRecord record, String tenantId, LocalDateTime now) {
        record.setTenantId(tenantId);
        record.setCreateBy(SYSTEM_OPERATOR);
        record.setCreateTime(now);
        record.setUpdateBy(SYSTEM_OPERATOR);
        record.setUpdateTime(now);
        record.setDeleteFlag(0);
    }

    private String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private record ReminderTarget(String userId, String username, String realname) {
    }

    private record TimeoutConfig(boolean enabled, int timeoutMinutes, int intervalMinutes,
            int maxRemindCount, List<String> channels) {

        private static TimeoutConfig disabled() {
            return new TimeoutConfig(false, 0, DEFAULT_INTERVAL_MINUTES, DEFAULT_MAX_REMIND_COUNT, List.of(CHANNEL_SITE));
        }

        private static TimeoutConfig parse(String timeoutJson) {
            try {
                JsonNode root = JSON_MAPPER.readTree(timeoutJson);
                boolean enabled = root.path("enabled").asBoolean(true);
                int timeoutMinutes = readInt(root, "timeoutMinutes", readInt(root, "durationMinutes", 0));
                int intervalMinutes = Math.max(readInt(root, "remindIntervalMinutes",
                        readInt(root, "intervalMinutes", DEFAULT_INTERVAL_MINUTES)), 1);
                int maxRemindCount = Math.max(readInt(root, "maxRemindCount", DEFAULT_MAX_REMIND_COUNT), 1);
                List<String> channels = readChannels(root.get("channels"));
                return new TimeoutConfig(enabled, timeoutMinutes, intervalMinutes, maxRemindCount, channels);
            } catch (Exception e) {
                log.warn("Workflow timeout config parse failed, config={}", timeoutJson, e);
                return disabled();
            }
        }

        private boolean supportsSiteChannel() {
            return channels == null || channels.isEmpty() || channels.contains(CHANNEL_SITE);
        }

        private static int readInt(JsonNode root, String field, int fallback) {
            return root.has(field) && root.get(field).canConvertToInt() ? root.path(field).asInt() : fallback;
        }

        private static List<String> readChannels(JsonNode channelsNode) {
            if (channelsNode == null || channelsNode.isNull()) {
                return List.of(CHANNEL_SITE);
            }
            if (!channelsNode.isArray()) {
                return List.of(CHANNEL_SITE);
            }
            List<String> values = new ArrayList<>();
            channelsNode.forEach(node -> {
                if (node.isTextual() && StringUtils.hasText(node.asText())) {
                    values.add(node.asText());
                }
            });
            return values.isEmpty() ? List.of(CHANNEL_SITE) : values;
        }
    }
}
