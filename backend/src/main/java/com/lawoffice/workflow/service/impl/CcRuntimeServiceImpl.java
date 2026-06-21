package com.lawoffice.workflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.message.constant.MessageConstants;
import com.lawoffice.message.req.MessageActionReq;
import com.lawoffice.message.req.SendMessageReq;
import com.lawoffice.message.service.IMessageService;
import com.lawoffice.message.vo.MessageSendResultVO;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.entity.UserDepart;
import com.lawoffice.system.entity.UserRole;
import com.lawoffice.system.mapper.UserDepartMapper;
import com.lawoffice.system.mapper.UserRoleMapper;
import com.lawoffice.util.EntityFillUtils;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.CcRecord;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.ProcessModel;
import com.lawoffice.workflow.entity.ProcessNodeConfig;
import com.lawoffice.workflow.entity.Task;
import com.lawoffice.workflow.mapper.CcRecordMapper;
import com.lawoffice.workflow.mapper.ProcessInstanceMapper;
import com.lawoffice.workflow.mapper.ProcessModelMapper;
import com.lawoffice.workflow.mapper.ProcessNodeConfigMapper;
import com.lawoffice.workflow.req.CcPageReq;
import com.lawoffice.workflow.service.IAssigneeResolveService;
import com.lawoffice.workflow.service.ICcRuntimeService;
import com.lawoffice.workflow.vo.CcRecordVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CcRuntimeServiceImpl implements ICcRuntimeService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CcRecordMapper ccRecordMapper;
    private final ProcessInstanceMapper processInstanceMapper;
    private final ProcessModelMapper processModelMapper;
    private final ProcessNodeConfigMapper processNodeConfigMapper;
    private final UserDepartMapper userDepartMapper;
    private final UserRoleMapper userRoleMapper;
    private final IAssigneeResolveService assigneeResolveService;
    private final IMessageService messageService;

    public CcRuntimeServiceImpl(CcRecordMapper ccRecordMapper,
            ProcessInstanceMapper processInstanceMapper,
            ProcessModelMapper processModelMapper,
            ProcessNodeConfigMapper processNodeConfigMapper,
            UserDepartMapper userDepartMapper,
            UserRoleMapper userRoleMapper,
            IAssigneeResolveService assigneeResolveService,
            IMessageService messageService) {
        this.ccRecordMapper = ccRecordMapper;
        this.processInstanceMapper = processInstanceMapper;
        this.processModelMapper = processModelMapper;
        this.processNodeConfigMapper = processNodeConfigMapper;
        this.userDepartMapper = userDepartMapper;
        this.userRoleMapper = userRoleMapper;
        this.assigneeResolveService = assigneeResolveService;
        this.messageService = messageService;
    }

    @Override
    public BaseResult<PageVO<CcRecordVO>> pageMine(CcPageReq req, RequestContext context) {
        try {
            String tenantId = RuntimeSupport.requireTenantId(context);
            String userId = RuntimeSupport.requireUserId(context);
            CcPageReq query = req == null ? new CcPageReq() : req;

            QueryWrapper<CcRecord> wrapper = new QueryWrapper<>();
            wrapper.eq("tenant_id", tenantId)
                    .eq("receiver_user_id", userId)
                    .eq("delete_flag", 0);
            if (StringUtils.hasText(query.getProcessInstanceId())) {
                wrapper.eq("process_instance_id", query.getProcessInstanceId());
            }
            if (StringUtils.hasText(query.getStatus())) {
                wrapper.eq("status", query.getStatus());
            }
            applyInstanceTitleFilter(wrapper, query, tenantId);
            applyCreateTimeFilter(wrapper, query);
            wrapper.orderByDesc("create_time");

            Page<CcRecord> page = new Page<>(Math.max(query.getPageNum(), 1), Math.max(query.getPageSize(), 1));
            Page<CcRecord> resultPage = ccRecordMapper.selectPage(page, wrapper);
            List<CcRecordVO> records = buildCcRecordVOs(resultPage.getRecords(), tenantId);
            return BaseResult.success(new PageVO<>(records, resultPage.getTotal(), resultPage.getCurrent(), resultPage.getSize()));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("查询我的抄送失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<CcRecordVO> markRead(String ccRecordId, RequestContext context) {
        try {
            String tenantId = RuntimeSupport.requireTenantId(context);
            String userId = RuntimeSupport.requireUserId(context);
            CcRecord record = requireMine(ccRecordId, tenantId, userId);
            if (WorkflowConstants.CcStatus.READ.equals(record.getStatus())) {
                return BaseResult.success(BeanUtil.toBean(record, CcRecordVO.class));
            }

            record.setStatus(WorkflowConstants.CcStatus.READ);
            record.setReadTime(LocalDateTime.now());
            EntityFillUtils.fillAuditFields(record, context, false);
            ccRecordMapper.updateById(record);
            return BaseResult.success(BeanUtil.toBean(record, CcRecordVO.class));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("标记抄送已读失败: " + e.getMessage());
        }
    }

    @Override
    public void triggerConfiguredCc(ProcessInstance processInstance, Task task, String triggerAction,
            String tenantId, RequestContext context) {
        if (processInstance == null || !StringUtils.hasText(triggerAction)) {
            return;
        }
        List<ProcessNodeConfig> configs = listTriggerConfigs(processInstance, task, triggerAction, tenantId);
        if (configs.isEmpty()) {
            return;
        }
        Map<String, CcReceiver> receivers = new LinkedHashMap<>();
        for (ProcessNodeConfig config : configs) {
            collectReceivers(processInstance, config, triggerAction, tenantId, receivers);
        }
        if (receivers.isEmpty()) {
            log.warn("审批抄送未解析到接收人，instanceId={}, triggerAction={}",
                    processInstance.getId(), triggerAction);
            return;
        }
        List<CcRecord> records = upsertCcRecords(processInstance, task, triggerAction, tenantId, context, receivers);
        sendCcMessage(processInstance, records, context);
    }

    private void applyInstanceTitleFilter(QueryWrapper<CcRecord> wrapper, CcPageReq query, String tenantId) {
        if (!StringUtils.hasText(query.getInstanceTitle())) {
            return;
        }
        List<String> instanceIds = processInstanceMapper.selectList(new QueryWrapper<ProcessInstance>()
                        .select("id")
                        .eq("tenant_id", tenantId)
                        .like("instance_title", query.getInstanceTitle())
                        .eq("delete_flag", 0))
                .stream()
                .map(ProcessInstance::getId)
                .toList();
        if (instanceIds.isEmpty()) {
            wrapper.eq("process_instance_id", "__none__");
            return;
        }
        wrapper.in("process_instance_id", instanceIds);
    }

    private void applyCreateTimeFilter(QueryWrapper<CcRecord> wrapper, CcPageReq query) {
        if (StringUtils.hasText(query.getCreateTimeGe())) {
            wrapper.ge("create_time", LocalDateTime.parse(query.getCreateTimeGe().replace(" ", "T")));
        }
        if (StringUtils.hasText(query.getCreateTimeLe())) {
            wrapper.le("create_time", LocalDateTime.parse(query.getCreateTimeLe().replace(" ", "T")));
        }
    }

    private List<CcRecordVO> buildCcRecordVOs(List<CcRecord> records, String tenantId) {
        if (records.isEmpty()) {
            return List.of();
        }
        Map<String, ProcessInstance> instanceMap = processInstanceMapper.selectList(new QueryWrapper<ProcessInstance>()
                        .in("id", records.stream().map(CcRecord::getProcessInstanceId).distinct().toList())
                        .eq("tenant_id", tenantId)
                        .eq("delete_flag", 0))
                .stream()
                .collect(Collectors.toMap(ProcessInstance::getId, item -> item, (left, right) -> left));
        if (instanceMap.isEmpty()) {
            return records.stream()
                    .map(record -> buildCcRecordVO(record, null, Map.of()))
                    .toList();
        }
        Map<String, ProcessModel> modelMap = processModelMapper.selectList(new QueryWrapper<ProcessModel>()
                        .in("id", instanceMap.values().stream().map(ProcessInstance::getProcessModelId).distinct().toList())
                        .eq("tenant_id", tenantId)
                        .eq("delete_flag", 0))
                .stream()
                .collect(Collectors.toMap(ProcessModel::getId, item -> item, (left, right) -> left));
        return records.stream()
                .map(record -> buildCcRecordVO(record, instanceMap.get(record.getProcessInstanceId()), modelMap))
                .toList();
    }

    private CcRecordVO buildCcRecordVO(CcRecord record, ProcessInstance instance,
            Map<String, ProcessModel> modelMap) {
        CcRecordVO vo = BeanUtil.toBean(record, CcRecordVO.class);
        if (instance == null) {
            return vo;
        }
        vo.setInstanceNo(instance.getInstanceNo());
        vo.setInstanceTitle(instance.getInstanceTitle());
        vo.setProcessStatus(instance.getStatus());
        vo.setStarterUserId(instance.getStarterUserId());
        vo.setStarterUsername(instance.getStarterUsername());
        vo.setStarterRealname(instance.getStarterRealname());
        ProcessModel model = modelMap.get(instance.getProcessModelId());
        if (model != null) {
            vo.setProcessName(model.getProcessName());
        }
        return vo;
    }

    private List<ProcessNodeConfig> listTriggerConfigs(ProcessInstance processInstance, Task task,
            String triggerAction, String tenantId) {
        QueryWrapper<ProcessNodeConfig> wrapper = new QueryWrapper<ProcessNodeConfig>()
                .eq("tenant_id", tenantId)
                .eq("process_model_id", processInstance.getProcessModelId())
                .isNotNull("cc_json")
                .ne("cc_json", "")
                .eq("delete_flag", 0);
        if (WorkflowConstants.CcTriggerAction.APPROVE.equals(triggerAction)) {
            if (task == null || !StringUtils.hasText(task.getNodeId())) {
                return List.of();
            }
            wrapper.eq("node_id", task.getNodeId());
        }
        return processNodeConfigMapper.selectList(wrapper)
                .stream()
                .filter(config -> containsEvent(config.getCcJson(), triggerAction))
                .toList();
    }

    private boolean containsEvent(String ccJson, String triggerAction) {
        JsonNode root = parseCcJson(ccJson);
        List<String> events = readTextArray(root, "events", "triggerActions");
        if (events.isEmpty()) {
            return false;
        }
        return events.stream().anyMatch(event -> triggerAction.equals(normalizeEvent(event)));
    }

    private String normalizeEvent(String event) {
        if ("node_approved".equals(event)) {
            return WorkflowConstants.CcTriggerAction.APPROVE;
        }
        return event;
    }

    private void collectReceivers(ProcessInstance processInstance, ProcessNodeConfig config, String triggerAction,
            String tenantId, Map<String, CcReceiver> receivers) {
        JsonNode root = parseCcJson(config.getCcJson());
        JsonNode targets = root.has("targets") ? root.get("targets") : root.get("receivers");
        if (targets == null || !targets.isArray()) {
            return;
        }
        for (JsonNode target : targets) {
            collectTargetReceivers(processInstance, config, target, triggerAction, tenantId, receivers);
        }
    }

    private void collectTargetReceivers(ProcessInstance processInstance, ProcessNodeConfig config, JsonNode target,
            String triggerAction, String tenantId, Map<String, CcReceiver> receivers) {
        String targetType = readFirstText(target, "targetType", "sourceType", "type");
        if (!StringUtils.hasText(targetType)) {
            return;
        }
        List<String> targetIds = readTargetIds(target, targetType);
        List<String> userIds = switch (targetType) {
            case WorkflowConstants.TargetType.USER -> targetIds;
            case WorkflowConstants.TargetType.ROLE -> listRoleUserIds(targetIds, tenantId);
            case WorkflowConstants.TargetType.DEPART -> listDepartUserIds(targetIds, tenantId);
            case WorkflowConstants.AssigneeType.STARTER_SUPERVISOR -> listStarterSupervisorUserIds(processInstance, tenantId);
            default -> List.of();
        };
        Map<String, User> users = assigneeResolveService.loadTenantActiveUsers(userIds, tenantId);
        for (User user : users.values()) {
            receivers.putIfAbsent(user.getId(), new CcReceiver(
                    user,
                    triggerAction,
                    targetType,
                    targetIds.isEmpty() ? null : targetIds.get(0),
                    config.getNodeId(),
                    config.getNodeName()));
        }
    }

    private List<String> readTargetIds(JsonNode target, String targetType) {
        if (WorkflowConstants.TargetType.USER.equals(targetType)) {
            return readTextArray(target, "targetIds", "userIds", "ids");
        }
        if (WorkflowConstants.TargetType.ROLE.equals(targetType)) {
            return readTextArray(target, "targetIds", "roleIds", "ids");
        }
        if (WorkflowConstants.TargetType.DEPART.equals(targetType)) {
            return readTextArray(target, "targetIds", "departIds", "ids");
        }
        return readTextArray(target, "targetIds", "ids");
    }

    private List<String> listRoleUserIds(List<String> roleIds, String tenantId) {
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return userRoleMapper.selectList(new QueryWrapper<UserRole>()
                        .select("user_id")
                        .in("role_id", roleIds)
                        .eq("tenant_id", tenantId)
                        .eq("delete_flag", 0))
                .stream()
                .map(UserRole::getUserId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private List<String> listDepartUserIds(List<String> departIds, String tenantId) {
        if (departIds.isEmpty()) {
            return List.of();
        }
        return userDepartMapper.selectList(new QueryWrapper<UserDepart>()
                        .select("user_id")
                        .in("dep_id", departIds)
                        .eq("tenant_id", tenantId)
                        .eq("delete_flag", 0))
                .stream()
                .map(UserDepart::getUserId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private List<String> listStarterSupervisorUserIds(ProcessInstance processInstance, String tenantId) {
        return userDepartMapper.selectList(new QueryWrapper<UserDepart>()
                        .select("supervisor_user_id", "primary_depart_flag")
                        .eq("tenant_id", tenantId)
                        .eq("user_id", processInstance.getStarterUserId())
                        .isNotNull("supervisor_user_id")
                        .ne("supervisor_user_id", "")
                        .eq("delete_flag", 0)
                        .orderByDesc("primary_depart_flag")
                        .orderByAsc("create_time"))
                .stream()
                .map(UserDepart::getSupervisorUserId)
                .filter(StringUtils::hasText)
                .findFirst()
                .map(List::of)
                .orElseGet(List::of);
    }

    private List<CcRecord> upsertCcRecords(ProcessInstance processInstance, Task task, String triggerAction,
            String tenantId, RequestContext context, Map<String, CcReceiver> receivers) {
        List<CcRecord> records = new ArrayList<>();
        for (CcReceiver receiver : receivers.values()) {
            CcRecord record = ccRecordMapper.selectOne(new QueryWrapper<CcRecord>()
                    .eq("tenant_id", tenantId)
                    .eq("process_instance_id", processInstance.getId())
                    .eq("receiver_user_id", receiver.user().getId())
                    .eq("delete_flag", 0));
            boolean create = record == null;
            if (create) {
                record = new CcRecord();
                record.setId(UUID.randomUUID().toString().replace("-", ""));
                record.setTenantId(tenantId);
                record.setProcessInstanceId(processInstance.getId());
                record.setProcessModelId(processInstance.getProcessModelId());
                EntityFillUtils.fillAuditFields(record, context, true);
            } else {
                EntityFillUtils.fillAuditFields(record, context, false);
            }
            fillCcRecord(record, processInstance, task, triggerAction, receiver);
            if (create) {
                ccRecordMapper.insert(record);
            } else {
                ccRecordMapper.updateById(record);
            }
            records.add(record);
        }
        return records;
    }

    private void fillCcRecord(CcRecord record, ProcessInstance processInstance, Task task, String triggerAction,
            CcReceiver receiver) {
        record.setProcessModelId(processInstance.getProcessModelId());
        record.setTaskId(task == null ? null : task.getId());
        record.setNodeId(task == null ? receiver.nodeId() : task.getNodeId());
        record.setNodeName(task == null ? receiver.nodeName() : task.getTaskName());
        record.setTriggerAction(triggerAction);
        record.setSourceType(receiver.sourceType());
        record.setSourceId(receiver.sourceId());
        record.setReceiverUserId(receiver.user().getId());
        record.setReceiverUsername(receiver.user().getUsername());
        record.setReceiverRealname(receiver.user().getRealname());
        record.setStatus(WorkflowConstants.CcStatus.UNREAD);
        record.setReadTime(null);
        record.setRemark(buildCcRemark(triggerAction, processInstance));
    }

    private String buildCcRemark(String triggerAction, ProcessInstance processInstance) {
        String actionName = switch (triggerAction) {
            case WorkflowConstants.CcTriggerAction.START -> "发起后抄送";
            case WorkflowConstants.CcTriggerAction.APPROVE -> "节点通过后抄送";
            case WorkflowConstants.CcTriggerAction.PROCESS_FINISHED -> "流程结束后抄送";
            default -> "抄送";
        };
        return actionName + "：" + processInstance.getInstanceTitle();
    }

    /**
     * 消息只是抄送通知通道，发送失败不能破坏主审批事务。
     */
    private void sendCcMessage(ProcessInstance processInstance, List<CcRecord> records, RequestContext context) {
        if (records.isEmpty() || !StringUtils.hasText(context.getUsername())) {
            return;
        }
        try {
            SendMessageReq req = new SendMessageReq();
            req.setTitle("审批抄送：" + processInstance.getInstanceTitle());
            req.setContent("你收到一条审批抄送，请进入审批中心查看详情。");
            req.setContentType(MessageConstants.CONTENT_TYPE_TEXT);
            req.setMessageType(MessageConstants.MESSAGE_TYPE_NOTICE);
            req.setPriority(MessageConstants.PRIORITY_NORMAL);
            req.setReceiverIds(records.stream().map(CcRecord::getReceiverUserId).distinct().toList());
            req.setActions(List.of(buildDetailMessageAction(processInstance)));
            MessageSendResultVO result = messageService.sendMessage(req, context.getUsername());
            if (result == null || !StringUtils.hasText(result.getMessageId())) {
                return;
            }
            for (CcRecord record : records) {
                record.setMessageId(result.getMessageId());
                EntityFillUtils.fillAuditFields(record, context, false);
                ccRecordMapper.updateById(record);
            }
        } catch (Exception e) {
            log.warn("审批抄送消息发送失败，instanceId={}", processInstance.getId(), e);
        }
    }

    private MessageActionReq buildDetailMessageAction(ProcessInstance processInstance) {
        MessageActionReq action = new MessageActionReq();
        action.setActionType(MessageConstants.ACTION_TYPE_INTERNAL_ROUTE);
        action.setActionName("查看审批");
        action.setRoutePath("/workflow/cc");
        action.setRouteQuery("{\"instanceId\":\"" + processInstance.getId() + "\"}");
        action.setBizType("workflow_cc");
        action.setBizId(processInstance.getId());
        action.setOpenType(MessageConstants.OPEN_TYPE_CURRENT);
        action.setSortOrder(1);
        return action;
    }

    private CcRecord requireMine(String ccRecordId, String tenantId, String userId) {
        if (!StringUtils.hasText(ccRecordId)) {
            throw new IllegalArgumentException("抄送记录ID不能为空");
        }
        CcRecord record = ccRecordMapper.selectOne(new QueryWrapper<CcRecord>()
                .eq("id", ccRecordId)
                .eq("tenant_id", tenantId)
                .eq("receiver_user_id", userId)
                .eq("delete_flag", 0));
        if (record == null) {
            throw new IllegalArgumentException("抄送记录不存在或无权访问");
        }
        return record;
    }

    private JsonNode parseCcJson(String ccJson) {
        try {
            return OBJECT_MAPPER.readTree(ccJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("抄送配置JSON不是合法JSON");
        }
    }

    private String readFirstText(JsonNode node, String... keys) {
        if (node == null) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && value.isTextual() && StringUtils.hasText(value.asText())) {
                return value.asText();
            }
        }
        return null;
    }

    private List<String> readTextArray(JsonNode node, String... keys) {
        if (node == null) {
            return List.of();
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isArray()) {
                List<String> result = new ArrayList<>();
                value.forEach(item -> {
                    if (item.isTextual() && StringUtils.hasText(item.asText())) {
                        result.add(item.asText());
                    }
                });
                return result;
            }
            if (value.isTextual() && StringUtils.hasText(value.asText())) {
                return List.of(value.asText());
            }
        }
        return List.of();
    }

    private record CcReceiver(
            User user,
            String triggerAction,
            String sourceType,
            String sourceId,
            String nodeId,
            String nodeName) {
    }
}
