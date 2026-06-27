package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.system.entity.DepartRole;
import com.lawoffice.system.entity.DepartRoleUser;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.entity.UserDepart;
import com.lawoffice.system.entity.UserRole;
import com.lawoffice.system.entity.UserTenant;
import com.lawoffice.system.mapper.DepartRoleMapper;
import com.lawoffice.system.mapper.DepartRoleUserMapper;
import com.lawoffice.system.mapper.UserDepartMapper;
import com.lawoffice.system.mapper.UserMapper;
import com.lawoffice.system.mapper.UserRoleMapper;
import com.lawoffice.system.mapper.UserTenantMapper;
import com.lawoffice.util.EntityFillUtils;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.dto.FlowableTaskInfo;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.ProcessInstanceAssignee;
import com.lawoffice.workflow.entity.ProcessModel;
import com.lawoffice.workflow.entity.ProcessNodeConfig;
import com.lawoffice.workflow.entity.Task;
import com.lawoffice.workflow.entity.TaskCandidate;
import com.lawoffice.workflow.mapper.ProcessInstanceAssigneeMapper;
import com.lawoffice.workflow.mapper.ProcessModelMapper;
import com.lawoffice.workflow.mapper.ProcessNodeConfigMapper;
import com.lawoffice.workflow.mapper.TaskCandidateMapper;
import com.lawoffice.workflow.mapper.TaskMapper;
import com.lawoffice.workflow.req.SelectedAssigneeReq;
import com.lawoffice.workflow.service.IAssigneeResolveService;
import com.lawoffice.workflow.service.IFlowableService;
import com.lawoffice.workflow.service.IInstanceStateService;
import com.lawoffice.workflow.service.ITaskNotificationService;
import com.lawoffice.workflow.vo.AssigneeOptionVO;
import com.lawoffice.workflow.vo.AssigneeSelectNodeVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

@Service
public class AssigneeResolveServiceImpl implements IAssigneeResolveService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String SELECT_TYPE_SINGLE = "single";
    private static final String SELECT_TYPE_MULTIPLE = "multiple";

    private record ResolvedAssignee(String userId, String username, String realname, String sourceType, String sourceId) {
    }

    private record NextNodeLookupResult(boolean resolvedByBpmn, Optional<ProcessNodeConfig> nodeConfig) {
    }

    private final DepartRoleUserMapper departRoleUserMapper;
    private final DepartRoleMapper departRoleMapper;
    private final IFlowableService flowableService;
    private final IInstanceStateService instanceStateService;
    private final ITaskNotificationService taskNotificationService;
    private final ProcessInstanceAssigneeMapper processInstanceAssigneeMapper;
    private final ProcessModelMapper processModelMapper;
    private final ProcessNodeConfigMapper processNodeConfigMapper;
    private final TaskCandidateMapper taskCandidateMapper;
    private final TaskMapper taskMapper;
    private final UserDepartMapper userDepartMapper;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserTenantMapper userTenantMapper;

    public AssigneeResolveServiceImpl(DepartRoleUserMapper departRoleUserMapper,
            DepartRoleMapper departRoleMapper,
            IFlowableService flowableService,
            IInstanceStateService instanceStateService,
            ITaskNotificationService taskNotificationService,
            ProcessInstanceAssigneeMapper processInstanceAssigneeMapper,
            ProcessModelMapper processModelMapper,
            ProcessNodeConfigMapper processNodeConfigMapper,
            TaskCandidateMapper taskCandidateMapper,
            TaskMapper taskMapper,
            UserDepartMapper userDepartMapper,
            UserMapper userMapper,
            UserRoleMapper userRoleMapper,
            UserTenantMapper userTenantMapper) {
        this.departRoleUserMapper = departRoleUserMapper;
        this.departRoleMapper = departRoleMapper;
        this.flowableService = flowableService;
        this.instanceStateService = instanceStateService;
        this.taskNotificationService = taskNotificationService;
        this.processInstanceAssigneeMapper = processInstanceAssigneeMapper;
        this.processModelMapper = processModelMapper;
        this.processNodeConfigMapper = processNodeConfigMapper;
        this.taskCandidateMapper = taskCandidateMapper;
        this.taskMapper = taskMapper;
        this.userDepartMapper = userDepartMapper;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.userTenantMapper = userTenantMapper;
    }

    @Override
    public String resolveDisplayName(String realname, String username, String userId) {
        if (StringUtils.hasText(realname)) {
            return realname;
        }
        if (StringUtils.hasText(username)) {
            return username;
        }
        return userId;
    }

    @Override
    public String resolveCurrentUserRealname(RequestContext context) {
        String userId = context == null ? null : context.getUserId();
        String username = context == null ? null : context.getUsername();
        User user = null;
        if (StringUtils.hasText(userId)) {
            user = userMapper.selectOne(new QueryWrapper<User>()
                    .select("id", "username", "realname")
                    .eq("id", userId)
                    .eq("delete_flag", 0)
                    .last("limit 1"));
        }
        if (user != null) {
            return resolveDisplayName(user.getRealname(), user.getUsername(), user.getId());
        }
        return resolveDisplayName(null, username, userId);
    }

    @Override
    public Map<String, User> loadTenantActiveUsers(List<String> userIds, String tenantId) {
        List<String> normalizedUserIds = userIds == null ? List.of() : userIds.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (normalizedUserIds.isEmpty()) {
            return Map.of();
        }
        Set<String> tenantUserIds = userTenantMapper.selectList(new QueryWrapper<UserTenant>()
                        .select("user_id")
                        .in("user_id", normalizedUserIds)
                        .eq("tenant_id", tenantId)
                        .eq("status", "1")
                        .eq("delete_flag", 0))
                .stream()
                .map(UserTenant::getUserId)
                .collect(java.util.stream.Collectors.toSet());
        if (tenantUserIds.isEmpty()) {
            return Map.of();
        }
        Map<String, User> userMap = new LinkedHashMap<>();
        userMapper.selectList(new QueryWrapper<User>()
                        .in("id", tenantUserIds)
                        .eq("status", 1)
                        .eq("delete_flag", 0))
                .forEach(user -> userMap.put(user.getId(), user));
        return userMap;
    }

    /**
     * 当前办理人提交前先解析真实下一审批节点，空审批人统一转审批人自选兜底。
     */
    @Override
    public List<AssigneeSelectNodeVO> buildRequiredAssigneeSelectNodes(String processModelId,
            ProcessInstance processInstance, String tenantId, String currentNodeId) {
        String targetNodeId = findNextApproverNodeConfig(processModelId, currentNodeId, tenantId)
                .map(ProcessNodeConfig::getNodeId)
                .orElse(null);
        if (!StringUtils.hasText(targetNodeId)) {
            return List.of();
        }
        return listApproverNodeConfigs(processModelId, tenantId).stream()
                .filter(nodeConfig -> targetNodeId.equals(nodeConfig.getNodeId()))
                .filter(this::requiresRuntimeAssigneeSelection)
                .map(nodeConfig -> buildAssigneeSelectNode(nodeConfig, processInstance, tenantId))
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 保存发起时节点审批人选择快照。快照只用于当前流程实例，组织关系后续变化不影响已提交实例。
     */
    @Override
    public void saveFirstAssigneeSnapshot(ProcessInstance processInstance, List<SelectedAssigneeReq> selectedAssignees,
            String tenantId, RequestContext context) {
        saveSelectedAssigneeSnapshots(processInstance, selectedAssignees, tenantId, context, WorkflowConstants.VirtualNode.START_DRAFT);
    }

    @Override
    public void saveNextAssigneeSnapshot(ProcessInstance processInstance, String currentNodeId,
            List<SelectedAssigneeReq> selectedAssignees, String tenantId, RequestContext context) {
        saveSelectedAssigneeSnapshots(processInstance, selectedAssignees, tenantId, context, currentNodeId);
    }

    @Override
    public void saveAssigneeSnapshotForNode(ProcessInstance processInstance, String nodeId,
            List<SelectedAssigneeReq> selectedAssignees, String tenantId, RequestContext context) {
        saveSelectedAssigneeSnapshots(processInstance, selectedAssignees, tenantId, context,
                buildAssigneeSelectNodesForNode(processInstance, nodeId, tenantId));
    }

    @Override
    public List<AssigneeSelectNodeVO> buildAssigneeSelectNodesForNode(ProcessInstance processInstance, String nodeId,
            String tenantId) {
        Optional<ProcessNodeConfig> nodeConfig = findRuntimeAssigneeNodeConfig(processInstance, nodeId, tenantId);
        if (nodeConfig.isEmpty()) {
            return List.of();
        }
        AssigneeSelectNodeVO requiredNode = buildAssigneeSelectNode(nodeConfig.get(), processInstance, tenantId);
        return requiredNode == null ? List.of() : List.of(requiredNode);
    }

    @Override
    public void syncCurrentTasks(ProcessInstance processInstance, String tenantId, RequestContext context) {
        if (!StringUtils.hasText(processInstance.getFlowableProcessInstanceId())) {
            instanceStateService.refreshCurrentTaskSummary(processInstance, tenantId);
            return;
        }
        List<FlowableTaskInfo> activeTasks = flowableService.listActiveTasks(processInstance.getFlowableProcessInstanceId());
        Set<String> existingFlowableTaskIds = new HashSet<>();
        if (!activeTasks.isEmpty()) {
            taskMapper.selectList(new QueryWrapper<Task>()
                            .select("flowable_task_id")
                            .eq("tenant_id", tenantId)
                            .eq("process_instance_id", processInstance.getId())
                            .in("flowable_task_id", activeTasks.stream().map(FlowableTaskInfo::getTaskId).toList())
                            .eq("delete_flag", 0))
                    .stream()
                    .map(Task::getFlowableTaskId)
                    .filter(StringUtils::hasText)
                    .forEach(existingFlowableTaskIds::add);
        }
        for (FlowableTaskInfo flowableTask : activeTasks) {
            if (existingFlowableTaskIds.contains(flowableTask.getTaskId())) {
                continue;
            }
            ProcessNodeConfig nodeConfig = requireNodeConfig(processInstance.getProcessModelId(), flowableTask.getTaskDefinitionKey(), tenantId);
            List<ResolvedAssignee> assignees = resolveTaskAssigneesForInstance(nodeConfig, processInstance, tenantId);
            applyFlowableAssignees(flowableTask.getTaskId(), assignees);
            createRuntimeTasks(processInstance, flowableTask, nodeConfig, assignees, context);
        }
        instanceStateService.refreshCurrentTaskSummary(processInstance, tenantId);
    }

    private Optional<ProcessNodeConfig> findNextApproverNodeConfig(String processModelId, String currentNodeId, String tenantId) {
        List<ProcessNodeConfig> nodes = listApproverNodeConfigs(processModelId, tenantId);
        if (nodes.isEmpty()) {
            return Optional.empty();
        }
        NextNodeLookupResult bpmnNext = findNextApproverNodeConfigByBpmn(processModelId, currentNodeId, tenantId, nodes);
        if (bpmnNext.resolvedByBpmn()) {
            return bpmnNext.nodeConfig();
        }
        if (!StringUtils.hasText(currentNodeId) || WorkflowConstants.VirtualNode.START_DRAFT.equals(currentNodeId)) {
            return Optional.of(nodes.get(0));
        }
        for (int i = 0; i < nodes.size(); i++) {
            if (currentNodeId.equals(nodes.get(i).getNodeId()) && i + 1 < nodes.size()) {
                return Optional.of(nodes.get(i + 1));
            }
        }
        return Optional.empty();
    }

    /**
     * 流程流转以 Flowable 的 BPMN 连线为准；前端在提交前选择下一审批人时必须和真实下一节点一致。
     */
    private NextNodeLookupResult findNextApproverNodeConfigByBpmn(String processModelId, String currentNodeId,
            String tenantId, List<ProcessNodeConfig> nodes) {
        ProcessModel model = processModelMapper.selectOne(new QueryWrapper<ProcessModel>()
                .select("id", "bpmn_xml")
                .eq("id", processModelId)
                .eq("tenant_id", tenantId)
                .eq("delete_flag", 0));
        if (model == null || !StringUtils.hasText(model.getBpmnXml())) {
            return new NextNodeLookupResult(false, Optional.empty());
        }
        String sourceNodeId = WorkflowConstants.VirtualNode.START_DRAFT.equals(currentNodeId) || !StringUtils.hasText(currentNodeId)
                ? findBpmnStartEventId(model.getBpmnXml()).orElse(null)
                : currentNodeId;
        if (!StringUtils.hasText(sourceNodeId)) {
            return new NextNodeLookupResult(false, Optional.empty());
        }
        Set<String> userTaskIds = nodes.stream()
                .map(ProcessNodeConfig::getNodeId)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
        Optional<ProcessNodeConfig> nextNode = findNextBpmnUserTaskId(model.getBpmnXml(), sourceNodeId, userTaskIds)
                .flatMap(nodeId -> nodes.stream()
                        .filter(node -> nodeId.equals(node.getNodeId()))
                        .findFirst());
        return new NextNodeLookupResult(true, nextNode);
    }

    private Optional<String> findBpmnStartEventId(String bpmnXml) {
        try {
            Document document = parseBpmnDocument(bpmnXml);
            NodeList startEvents = document.getElementsByTagNameNS("*", "startEvent");
            if (startEvents.getLength() == 0) {
                return Optional.empty();
            }
            String startEventId = ((Element) startEvents.item(0)).getAttribute("id");
            return StringUtils.hasText(startEventId) ? Optional.of(startEventId) : Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<String> findNextBpmnUserTaskId(String bpmnXml, String sourceNodeId, Set<String> userTaskIds) {
        try {
            Document document = parseBpmnDocument(bpmnXml);
            Map<String, List<String>> targetIdsBySourceId = new LinkedHashMap<>();
            NodeList sequenceFlows = document.getElementsByTagNameNS("*", "sequenceFlow");
            for (int i = 0; i < sequenceFlows.getLength(); i++) {
                Element element = (Element) sequenceFlows.item(i);
                String sourceRef = element.getAttribute("sourceRef");
                String targetRef = element.getAttribute("targetRef");
                if (StringUtils.hasText(sourceRef) && StringUtils.hasText(targetRef)) {
                    targetIdsBySourceId.computeIfAbsent(sourceRef, key -> new ArrayList<>()).add(targetRef);
                }
            }
            Set<String> visited = new HashSet<>();
            return findReachableUserTaskId(sourceNodeId, targetIdsBySourceId, userTaskIds, visited);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<String> findReachableUserTaskId(String sourceNodeId, Map<String, List<String>> targetIdsBySourceId,
            Set<String> userTaskIds, Set<String> visited) {
        if (!visited.add(sourceNodeId)) {
            return Optional.empty();
        }
        for (String targetId : targetIdsBySourceId.getOrDefault(sourceNodeId, List.of())) {
            if (userTaskIds.contains(targetId)) {
                return Optional.of(targetId);
            }
            Optional<String> nested = findReachableUserTaskId(targetId, targetIdsBySourceId, userTaskIds, visited);
            if (nested.isPresent()) {
                return nested;
            }
        }
        return Optional.empty();
    }

    private Document parseBpmnDocument(String bpmnXml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(bpmnXml)));
    }

    private List<ProcessNodeConfig> listApproverNodeConfigs(String processModelId, String tenantId) {
        return processNodeConfigMapper.selectList(new QueryWrapper<ProcessNodeConfig>()
                .eq("tenant_id", tenantId)
                .eq("process_model_id", processModelId)
                .isNotNull("assignee_type")
                .ne("assignee_type", "")
                .eq("delete_flag", 0)
                .orderByAsc("sort_order")
                .orderByAsc("create_time"));
    }

    /**
     * 条件分支预判已经确定目标节点时，只需要判断该节点本身是否需要运行时选择审批人。
     */
    private Optional<ProcessNodeConfig> findRuntimeAssigneeNodeConfig(ProcessInstance processInstance,
            String nodeId, String tenantId) {
        if (!StringUtils.hasText(nodeId)) {
            return Optional.empty();
        }
        ProcessNodeConfig nodeConfig = processNodeConfigMapper.selectOne(new QueryWrapper<ProcessNodeConfig>()
                .eq("tenant_id", tenantId)
                .eq("process_model_id", processInstance.getProcessModelId())
                .eq("node_id", nodeId)
                .eq("delete_flag", 0));
        if (nodeConfig == null || !StringUtils.hasText(nodeConfig.getAssigneeType())
                || !requiresRuntimeAssigneeSelection(nodeConfig)) {
            return Optional.empty();
        }
        return Optional.of(nodeConfig);
    }

    private boolean requiresRuntimeAssigneeSelection(ProcessNodeConfig nodeConfig) {
        return WorkflowConstants.AssigneeType.USER.equals(nodeConfig.getAssigneeType())
                || WorkflowConstants.AssigneeType.ROLE.equals(nodeConfig.getAssigneeType())
                || WorkflowConstants.AssigneeType.DEPART_LEADER.equals(nodeConfig.getAssigneeType())
                || WorkflowConstants.AssigneeType.DEPART_ROLE.equals(nodeConfig.getAssigneeType())
                || WorkflowConstants.AssigneeType.STARTER_SUPERVISOR.equals(nodeConfig.getAssigneeType())
                || WorkflowConstants.AssigneeType.STARTER_SELECT.equals(nodeConfig.getAssigneeType())
                || WorkflowConstants.AssigneeType.STARTER.equals(nodeConfig.getAssigneeType());
    }

    private boolean requiresExplicitAssigneeSelection(ProcessNodeConfig nodeConfig) {
        return WorkflowConstants.AssigneeType.USER.equals(nodeConfig.getAssigneeType())
                || WorkflowConstants.AssigneeType.ROLE.equals(nodeConfig.getAssigneeType())
                || WorkflowConstants.AssigneeType.DEPART_ROLE.equals(nodeConfig.getAssigneeType())
                || WorkflowConstants.AssigneeType.STARTER_SELECT.equals(nodeConfig.getAssigneeType());
    }

    /**
     * 只有配置为上一步选择时，才把候选范围暴露给当前办理人；发送全部模式直接由运行时创建任务。
     */
    private AssigneeSelectNodeVO buildAssigneeSelectNode(ProcessNodeConfig nodeConfig,
            ProcessInstance processInstance, String tenantId) {
        if (WorkflowConstants.AssigneeType.STARTER_SELECT.equals(nodeConfig.getAssigneeType())) {
            return buildStarterSelectNode(nodeConfig, false);
        }
        List<ResolvedAssignee> assignees;
        try {
            assignees = resolveTaskAssignees(nodeConfig, processInstance, tenantId);
        } catch (IllegalArgumentException e) {
            return buildStarterSelectNode(nodeConfig, true);
        }
        if (!WorkflowConstants.AssigneeResolveMode.SELECT.equals(normalizeAssigneeResolveMode(nodeConfig))) {
            return null;
        }
        if (assignees.size() <= 1
                || !requiresExplicitAssigneeSelection(nodeConfig)) {
            return null;
        }
        AssigneeSelectNodeVO vo = new AssigneeSelectNodeVO();
        vo.setNodeId(nodeConfig.getNodeId());
        vo.setNodeName(nodeConfig.getNodeName());
        vo.setAssigneeType(nodeConfig.getAssigneeType());
        vo.setSelectType(resolveSelectType(nodeConfig.getApprovalMode()));
        vo.setRequired(true);
        vo.setOptions(assignees.stream().map(this::buildAssigneeOption).toList());
        return vo;
    }

    private AssigneeSelectNodeVO buildStarterSelectNode(ProcessNodeConfig nodeConfig, boolean fallback) {
        AssigneeSelectNodeVO vo = new AssigneeSelectNodeVO();
        vo.setNodeId(nodeConfig.getNodeId());
        vo.setNodeName(nodeConfig.getNodeName());
        vo.setAssigneeType(WorkflowConstants.AssigneeType.STARTER_SELECT);
        vo.setSelectType(resolveSelectType(nodeConfig.getApprovalMode()));
        vo.setRequired(true);
        vo.setFallback(fallback);
        if (fallback) {
            vo.setWarningMessage("下一节点未解析到审批人，请手动选择下一审批人");
        }
        vo.setOptions(List.of());
        return vo;
    }

    private AssigneeOptionVO buildAssigneeOption(ResolvedAssignee assignee) {
        AssigneeOptionVO vo = new AssigneeOptionVO();
        vo.setUserId(assignee.userId());
        vo.setUsername(assignee.username());
        vo.setRealname(assignee.realname());
        vo.setDisplayName(resolveDisplayName(assignee.realname(), assignee.username(), assignee.userId()));
        vo.setSourceType(assignee.sourceType());
        vo.setSourceId(assignee.sourceId());
        return vo;
    }

    private void saveSelectedAssigneeSnapshots(ProcessInstance processInstance, List<SelectedAssigneeReq> selectedAssignees,
            String tenantId, RequestContext context, String currentNodeId) {
        List<AssigneeSelectNodeVO> requiredNodes = buildRequiredAssigneeSelectNodes(
                processInstance.getProcessModelId(), processInstance, tenantId, currentNodeId);
        saveSelectedAssigneeSnapshots(processInstance, selectedAssignees, tenantId, context, requiredNodes);
    }

    /**
     * 统一执行节点审批人选择结果的校验和快照写入；顺序流和条件分支只在“需要保存哪些节点”上不同。
     */
    private void saveSelectedAssigneeSnapshots(ProcessInstance processInstance, List<SelectedAssigneeReq> selectedAssignees,
            String tenantId, RequestContext context, List<AssigneeSelectNodeVO> requiredNodes) {
        Set<String> requiredNodeIds = requiredNodes.stream()
                .map(AssigneeSelectNodeVO::getNodeId)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
        if (requiredNodeIds.isEmpty()) {
            return;
        }
        processInstanceAssigneeMapper.update(null, new UpdateWrapper<ProcessInstanceAssignee>()
                .eq("tenant_id", tenantId)
                .eq("process_instance_id", processInstance.getId())
                .in("node_id", requiredNodeIds)
                .eq("delete_flag", 0)
                .set("delete_flag", 1)
                .set("delete_by", context.getUsername())
                .set("delete_time", LocalDateTime.now())
                .set("update_by", context.getUsername())
                .set("update_time", LocalDateTime.now()));
        Map<String, SelectedAssigneeReq> selectedByNodeId = new HashMap<>();
        if (selectedAssignees != null) {
            for (SelectedAssigneeReq selected : selectedAssignees) {
                if (selected != null && StringUtils.hasText(selected.getNodeId())) {
                    selectedByNodeId.put(selected.getNodeId(), selected);
                }
            }
        }
        for (AssigneeSelectNodeVO node : requiredNodes) {
            SelectedAssigneeReq selected = selectedByNodeId.get(node.getNodeId());
            List<String> selectedUserIds = selected == null || selected.getUserIds() == null
                    ? List.of()
                    : selected.getUserIds().stream().filter(StringUtils::hasText).distinct().toList();
            if (SELECT_TYPE_MULTIPLE.equals(node.getSelectType())) {
                if (selectedUserIds.isEmpty()) {
                    throw new IllegalArgumentException("请选择节点审批人: " + node.getNodeName());
                }
            } else if (selectedUserIds.size() != 1) {
                throw new IllegalArgumentException("请选择节点审批人: " + node.getNodeName());
            }
            for (String selectedUserId : selectedUserIds) {
                ResolvedAssignee selectedAssignee = resolveSelectedAssignee(node, selectedUserId, tenantId);
                if (selectedAssignee == null) {
                    throw new IllegalArgumentException("节点审批人不在允许范围内: " + node.getNodeName());
                }
                ProcessInstanceAssignee snapshot = new ProcessInstanceAssignee();
                snapshot.setTenantId(tenantId);
                snapshot.setProcessInstanceId(processInstance.getId());
                snapshot.setProcessModelId(processInstance.getProcessModelId());
                snapshot.setNodeId(node.getNodeId());
                snapshot.setNodeName(node.getNodeName());
                snapshot.setAssigneeType(node.getAssigneeType());
                snapshot.setAssigneeUserId(selectedAssignee.userId());
                snapshot.setAssigneeUsername(selectedAssignee.username());
                snapshot.setAssigneeRealname(selectedAssignee.realname());
                snapshot.setSourceType(selectedAssignee.sourceType());
                snapshot.setSourceId(selectedAssignee.sourceId());
                snapshot.setSelectType(node.getSelectType());
                snapshot.setStatus(WorkflowConstants.Status.ACTIVE);
                EntityFillUtils.fillAuditFields(snapshot, context, true);
                processInstanceAssigneeMapper.insert(snapshot);
            }
        }
    }

    private ResolvedAssignee resolveSelectedAssignee(AssigneeSelectNodeVO node, String selectedUserId, String tenantId) {
        if (WorkflowConstants.AssigneeType.STARTER_SELECT.equals(node.getAssigneeType())) {
            User user = loadTenantActiveUsers(List.of(selectedUserId), tenantId).get(selectedUserId);
            return user == null ? null : new ResolvedAssignee(user.getId(), user.getUsername(), user.getRealname(),
                    WorkflowConstants.AssigneeType.STARTER_SELECT, user.getId());
        }
        List<AssigneeOptionVO> options = node.getOptions() == null ? List.of() : node.getOptions();
        Map<String, AssigneeOptionVO> optionByUserId = options.stream()
                .collect(java.util.stream.Collectors.toMap(AssigneeOptionVO::getUserId, option -> option, (left, right) -> left));
        AssigneeOptionVO option = optionByUserId.get(selectedUserId);
        return option == null ? null : new ResolvedAssignee(option.getUserId(), option.getUsername(), option.getRealname(),
                option.getSourceType(), option.getSourceId());
    }

    private List<ResolvedAssignee> resolveTaskAssigneesForInstance(ProcessNodeConfig nodeConfig,
            ProcessInstance processInstance, String tenantId) {
        List<ProcessInstanceAssignee> snapshots = processInstanceAssigneeMapper.selectList(new QueryWrapper<ProcessInstanceAssignee>()
                .eq("tenant_id", tenantId)
                .eq("process_instance_id", processInstance.getId())
                .eq("node_id", nodeConfig.getNodeId())
                .eq("status", WorkflowConstants.Status.ACTIVE)
                .eq("delete_flag", 0)
                .orderByAsc("create_time"));
        if (!snapshots.isEmpty()) {
            return snapshots.stream()
                    .map(snapshot -> new ResolvedAssignee(
                            snapshot.getAssigneeUserId(),
                            snapshot.getAssigneeUsername(),
                            snapshot.getAssigneeRealname(),
                            snapshot.getSourceType(),
                            snapshot.getSourceId()))
                    .toList();
        }
        if (WorkflowConstants.AssigneeType.STARTER_SELECT.equals(nodeConfig.getAssigneeType())) {
            throw new IllegalArgumentException("请选择下一审批人: " + nodeConfig.getNodeName());
        }
        List<ResolvedAssignee> assignees = resolveTaskAssignees(nodeConfig, processInstance, tenantId);
        if (requiresExplicitAssigneeSelection(nodeConfig)
                && WorkflowConstants.AssigneeResolveMode.SELECT.equals(normalizeAssigneeResolveMode(nodeConfig))
                && assignees.size() > 1) {
            throw new IllegalArgumentException("节点存在多个可选审批人，请选择后再提交: " + nodeConfig.getNodeName());
        }
        return assignees;
    }

    /**
     * 解析系统主数据中的审批人，Flowable 不作为用户、角色或部门数据来源。
     */
    private List<ResolvedAssignee> resolveTaskAssignees(ProcessNodeConfig nodeConfig, ProcessInstance processInstance, String tenantId) {
        List<ResolvedAssignee> assignees = switch (nodeConfig.getAssigneeType()) {
            case WorkflowConstants.AssigneeType.USER -> resolveUserAssignees(nodeConfig, tenantId);
            case WorkflowConstants.AssigneeType.ROLE -> resolveRoleAssignees(nodeConfig, tenantId);
            case WorkflowConstants.AssigneeType.DEPART_LEADER -> resolveDepartLeaderAssignees(nodeConfig, processInstance, tenantId);
            case WorkflowConstants.AssigneeType.DEPART_ROLE -> resolveDepartRoleAssignees(nodeConfig, tenantId);
            case WorkflowConstants.AssigneeType.STARTER_SUPERVISOR -> resolveStarterSupervisorAssignees(processInstance, tenantId);
            case WorkflowConstants.AssigneeType.STARTER -> resolveStarterAssignee(processInstance, tenantId);
            default -> throw new IllegalArgumentException("不支持的审批人类型: " + nodeConfig.getAssigneeType());
        };
        if (assignees.isEmpty()) {
            throw new IllegalArgumentException("未解析到审批人: " + nodeConfig.getNodeName());
        }
        return assignees;
    }

    private List<ResolvedAssignee> resolveUserAssignees(ProcessNodeConfig nodeConfig, String tenantId) {
        List<String> userIds = readIdList(nodeConfig.getAssigneeJson(), "userIds", "users", "ids");
        Map<String, User> users = loadTenantActiveUsers(userIds, tenantId);
        return users.values().stream()
                .map(user -> new ResolvedAssignee(user.getId(), user.getUsername(), user.getRealname(),
                        WorkflowConstants.TargetType.USER, user.getId()))
                .toList();
    }

    private List<ResolvedAssignee> resolveRoleAssignees(ProcessNodeConfig nodeConfig, String tenantId) {
        List<String> roleIds = readIdList(nodeConfig.getAssigneeJson(), "roleIds", "roles", "ids");
        if (roleIds.isEmpty()) {
            return List.of();
        }
        List<UserRole> userRoles = userRoleMapper.selectList(new QueryWrapper<UserRole>()
                .in("role_id", roleIds)
                .eq("tenant_id", tenantId)
                .eq("delete_flag", 0));
        Map<String, String> sourceRoleByUserId = new LinkedHashMap<>();
        for (UserRole userRole : userRoles) {
            sourceRoleByUserId.putIfAbsent(userRole.getUserId(), userRole.getRoleId());
        }
        Map<String, User> users = loadTenantActiveUsers(new ArrayList<>(sourceRoleByUserId.keySet()), tenantId);
        return users.values().stream()
                .map(user -> new ResolvedAssignee(user.getId(), user.getUsername(), user.getRealname(),
                        WorkflowConstants.TargetType.ROLE, sourceRoleByUserId.get(user.getId())))
                .toList();
    }

    private List<ResolvedAssignee> resolveDepartLeaderAssignees(ProcessNodeConfig nodeConfig,
            ProcessInstance processInstance, String tenantId) {
        String departId = resolveStarterCurrentDepartId(processInstance, tenantId);
        if (!StringUtils.hasText(departId)) {
            return List.of();
        }
        List<String> leaderUserIds = userDepartMapper.selectList(new QueryWrapper<UserDepart>()
                        .select("user_id")
                        .eq("tenant_id", tenantId)
                        .eq("dep_id", departId)
                        .eq("depart_leader_flag", 1)
                        .eq("delete_flag", 0))
                .stream()
                .map(UserDepart::getUserId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (leaderUserIds.isEmpty()) {
            return List.of();
        }
        Map<String, User> leaders = loadTenantActiveUsers(leaderUserIds, tenantId);
        return leaderUserIds.stream()
                .map(leaders::get)
                .filter(Objects::nonNull)
                .distinct()
                .map(user -> new ResolvedAssignee(user.getId(), user.getUsername(), user.getRealname(),
                        WorkflowConstants.AssigneeType.DEPART_LEADER, departId))
                .toList();
    }

    private String resolveStarterCurrentDepartId(ProcessInstance processInstance, String tenantId) {
        List<UserDepart> starterDeparts = userDepartMapper.selectList(new QueryWrapper<UserDepart>()
                .select("dep_id", "primary_depart_flag")
                .eq("tenant_id", tenantId)
                .eq("user_id", processInstance.getStarterUserId())
                .eq("delete_flag", 0)
                .orderByDesc("primary_depart_flag")
                .orderByAsc("create_time"));
        return starterDeparts.stream()
                .map(UserDepart::getDepId)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private List<ResolvedAssignee> resolveDepartRoleAssignees(ProcessNodeConfig nodeConfig, String tenantId) {
        List<String> departRoleIds = readIdList(nodeConfig.getAssigneeJson(), "departRoleIds", "departRoles", "ids");
        if (departRoleIds.isEmpty()) {
            return List.of();
        }
        Set<String> workflowDepartRoleIds = departRoleMapper.selectList(new QueryWrapper<DepartRole>()
                        .select("id")
                        .in("id", departRoleIds)
                        .eq("tenant_id", tenantId)
                        .eq("workflow_enabled", 1)
                        .eq("delete_flag", 0))
                .stream()
                .map(DepartRole::getId)
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toSet());
        if (workflowDepartRoleIds.isEmpty()) {
            return List.of();
        }
        List<DepartRoleUser> roleUsers = departRoleUserMapper.selectList(new QueryWrapper<DepartRoleUser>()
                .in("drole_id", workflowDepartRoleIds)
                .eq("tenant_id", tenantId)
                .eq("delete_flag", 0));
        Map<String, String> sourceRoleByUserId = new LinkedHashMap<>();
        for (DepartRoleUser roleUser : roleUsers) {
            sourceRoleByUserId.putIfAbsent(roleUser.getUserId(), roleUser.getDroleId());
        }
        Map<String, User> users = loadTenantActiveUsers(new ArrayList<>(sourceRoleByUserId.keySet()), tenantId);
        return users.values().stream()
                .filter(user -> workflowDepartRoleIds.contains(sourceRoleByUserId.get(user.getId())))
                .map(user -> new ResolvedAssignee(user.getId(), user.getUsername(), user.getRealname(),
                        WorkflowConstants.AssigneeType.DEPART_ROLE, sourceRoleByUserId.get(user.getId())))
                .toList();
    }

    private List<ResolvedAssignee> resolveStarterSupervisorAssignees(ProcessInstance processInstance, String tenantId) {
        String supervisorUserId = userDepartMapper.selectList(new QueryWrapper<UserDepart>()
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
                .orElse(null);
        if (!StringUtils.hasText(supervisorUserId)) {
            return List.of();
        }
        Map<String, User> users = loadTenantActiveUsers(List.of(supervisorUserId), tenantId);
        User supervisor = users.get(supervisorUserId);
        return supervisor == null
                ? List.of()
                : List.of(new ResolvedAssignee(
                        supervisor.getId(),
                        supervisor.getUsername(),
                        supervisor.getRealname(),
                        WorkflowConstants.AssigneeType.STARTER_SUPERVISOR,
                        supervisor.getId()));
    }

    private List<ResolvedAssignee> resolveStarterAssignee(ProcessInstance processInstance, String tenantId) {
        Map<String, User> users = loadTenantActiveUsers(List.of(processInstance.getStarterUserId()), tenantId);
        return users.values().stream()
                .map(user -> new ResolvedAssignee(user.getId(), user.getUsername(), user.getRealname(),
                        WorkflowConstants.AssigneeType.STARTER, user.getId()))
                .toList();
    }

    private List<String> readIdList(String json, String... fieldNames) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            List<String> ids = new ArrayList<>();
            if (root.isArray()) {
                root.forEach(node -> addTextValue(ids, node));
            } else if (root.isObject()) {
                for (String fieldName : fieldNames) {
                    JsonNode node = root.get(fieldName);
                    if (node == null) {
                        continue;
                    }
                    if (node.isArray()) {
                        node.forEach(item -> addTextValue(ids, item));
                    } else {
                        addTextValue(ids, node);
                    }
                }
            }
            return ids.stream().filter(StringUtils::hasText).distinct().toList();
        } catch (Exception e) {
            throw new IllegalArgumentException("审批人配置JSON不合法");
        }
    }

    private void addTextValue(List<String> values, JsonNode node) {
        if (node != null && node.isTextual() && StringUtils.hasText(node.asText())) {
            values.add(node.asText());
        }
    }

    private void applyFlowableAssignees(String flowableTaskId, List<ResolvedAssignee> assignees) {
        if (assignees.size() == 1) {
            flowableService.setTaskAssignee(flowableTaskId, assignees.get(0).userId());
            return;
        }
        flowableService.addCandidateUsers(flowableTaskId, assignees.stream()
                .map(ResolvedAssignee::userId)
                .toList());
    }

    /**
     * 根据节点办理策略生成业务任务。会签/或签需要多条 wf_task 表达多个办理人，
     * 但 Flowable 只有一个用户任务令牌，因此只让组内第一条任务持有真实 Flowable taskId。
     */
    private void createRuntimeTasks(ProcessInstance processInstance, FlowableTaskInfo flowableTask,
            ProcessNodeConfig nodeConfig, List<ResolvedAssignee> assignees, RequestContext context) {
        String approvalMode = normalizeApprovalMode(nodeConfig.getApprovalMode());
        if (!isMultiApprovalMode(approvalMode)) {
            createSingleRuntimeTask(processInstance, flowableTask, nodeConfig, approvalMode, assignees, context);
            return;
        }
        createGroupRuntimeTasks(processInstance, flowableTask, nodeConfig, approvalMode, assignees, context);
    }

    /**
     * 单人审批保留一期语义：解析出一人时直接分配，解析出多人时仍作为候选抢办处理。
     */
    private void createSingleRuntimeTask(ProcessInstance processInstance, FlowableTaskInfo flowableTask,
            ProcessNodeConfig nodeConfig, String approvalMode, List<ResolvedAssignee> assignees,
            RequestContext context) {
        Task task = buildBaseTask(processInstance, flowableTask, nodeConfig, WorkflowConstants.TaskType.NORMAL, approvalMode);
        if (assignees.size() == 1) {
            fillTaskAssignee(task, assignees.get(0));
        }
        EntityFillUtils.fillAuditFields(task, context, true);
        taskMapper.insert(task);
        if (assignees.size() > 1) {
            createTaskCandidates(task, assignees, context);
        }
        taskNotificationService.sendTodoArrivalMessage(processInstance, task,
                assignees.stream().map(ResolvedAssignee::userId).toList(), context);
    }

    /**
     * 会签/或签为每个办理人创建独立业务待办，同组任务用 taskGroupId 关联。
     * 其它任务使用本地 Flowable 标识是为了避开 wf_task 的唯一索引，真实引擎任务由组内锚点任务保存。
     */
    private void createGroupRuntimeTasks(ProcessInstance processInstance, FlowableTaskInfo flowableTask,
            ProcessNodeConfig nodeConfig, String approvalMode, List<ResolvedAssignee> assignees,
            RequestContext context) {
        String groupId = newId();
        String anchorTaskId = null;
        for (int i = 0; i < assignees.size(); i++) {
            ResolvedAssignee assignee = assignees.get(i);
            Task task = buildBaseTask(processInstance, flowableTask, nodeConfig, approvalMode, approvalMode);
            task.setId(newId());
            task.setTaskGroupId(groupId);
            task.setGroupTotal(assignees.size());
            task.setGroupCompleted(0);
            if (i == 0) {
                anchorTaskId = task.getId();
            } else {
                task.setParentTaskId(anchorTaskId);
                task.setFlowableTaskId("group:" + task.getId());
            }
            fillTaskAssignee(task, assignee);
            EntityFillUtils.fillAuditFields(task, context, true);
            taskMapper.insert(task);
            taskNotificationService.sendTodoArrivalMessage(processInstance, task, context);
        }
    }

    private Task buildBaseTask(ProcessInstance processInstance, FlowableTaskInfo flowableTask, ProcessNodeConfig nodeConfig,
            String taskType, String approvalMode) {
        Task task = new Task();
        task.setTenantId(processInstance.getTenantId());
        task.setProcessInstanceId(processInstance.getId());
        task.setFlowableTaskId(flowableTask.getTaskId());
        task.setNodeId(flowableTask.getTaskDefinitionKey());
        task.setTaskName(flowableTask.getTaskName());
        task.setTaskType(taskType);
        task.setApprovalMode(approvalMode);
        task.setOwnerUsername(flowableTask.getOwner());
        task.setStatus(WorkflowConstants.Status.TODO);
        task.setDueTime(resolveDueTime(nodeConfig));
        task.setRemindCount(0);
        return task;
    }

    /**
     * 超时提醒以业务待办创建时间为起点，提前写入截止时间便于定时扫描走任务索引。
     */
    private LocalDateTime resolveDueTime(ProcessNodeConfig nodeConfig) {
        if (nodeConfig == null || !StringUtils.hasText(nodeConfig.getTimeoutJson())) {
            return null;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(nodeConfig.getTimeoutJson());
            if (!root.path("enabled").asBoolean(true)) {
                return null;
            }
            int minutes = root.has("timeoutMinutes")
                    ? root.path("timeoutMinutes").asInt(0)
                    : root.path("durationMinutes").asInt(0);
            return minutes > 0 ? LocalDateTime.now().plusMinutes(minutes) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void fillTaskAssignee(Task task, ResolvedAssignee assignee) {
        task.setAssigneeUserId(assignee.userId());
        task.setAssigneeUsername(assignee.username());
        task.setAssigneeRealname(assignee.realname());
    }

    private String resolveSelectType(String approvalMode) {
        return isMultiApprovalMode(approvalMode) ? SELECT_TYPE_MULTIPLE : SELECT_TYPE_SINGLE;
    }

    private boolean isMultiApprovalMode(String approvalMode) {
        return WorkflowConstants.ApprovalMode.COUNTERSIGN.equals(approvalMode)
                || WorkflowConstants.ApprovalMode.ORSIGN.equals(approvalMode);
    }

    private String normalizeApprovalMode(String approvalMode) {
        return isMultiApprovalMode(approvalMode) ? approvalMode : WorkflowConstants.ApprovalMode.SINGLE;
    }

    private String normalizeAssigneeResolveMode(ProcessNodeConfig nodeConfig) {
        if (WorkflowConstants.ApprovalMode.SINGLE.equals(normalizeApprovalMode(nodeConfig.getApprovalMode()))) {
            return WorkflowConstants.AssigneeResolveMode.SELECT;
        }
        if (WorkflowConstants.AssigneeResolveMode.ALL.equals(nodeConfig.getAssigneeResolveMode())
                || WorkflowConstants.AssigneeResolveMode.SELECT.equals(nodeConfig.getAssigneeResolveMode())) {
            return nodeConfig.getAssigneeResolveMode();
        }
        return WorkflowConstants.ApprovalMode.ORSIGN.equals(normalizeApprovalMode(nodeConfig.getApprovalMode()))
                ? WorkflowConstants.AssigneeResolveMode.ALL
                : WorkflowConstants.AssigneeResolveMode.SELECT;
    }

    private void createTaskCandidates(Task task, List<ResolvedAssignee> assignees, RequestContext context) {
        for (ResolvedAssignee assignee : assignees) {
            TaskCandidate candidate = new TaskCandidate();
            candidate.setTenantId(task.getTenantId());
            candidate.setTaskId(task.getId());
            candidate.setFlowableTaskId(task.getFlowableTaskId());
            candidate.setCandidateUserId(assignee.userId());
            candidate.setCandidateUsername(assignee.username());
            candidate.setCandidateRealname(assignee.realname());
            candidate.setSourceType(assignee.sourceType());
            candidate.setSourceId(assignee.sourceId());
            candidate.setStatus(WorkflowConstants.Status.ACTIVE);
            EntityFillUtils.fillAuditFields(candidate, context, true);
            taskCandidateMapper.insert(candidate);
        }
    }

    private ProcessNodeConfig requireNodeConfig(String processModelId, String nodeId, String tenantId) {
        ProcessNodeConfig nodeConfig = processNodeConfigMapper.selectOne(new QueryWrapper<ProcessNodeConfig>()
                .eq("tenant_id", tenantId)
                .eq("process_model_id", processModelId)
                .eq("node_id", nodeId)
                .eq("delete_flag", 0));
        if (nodeConfig == null) {
            throw new IllegalArgumentException("流程节点未配置审批人: " + nodeId);
        }
        if (!StringUtils.hasText(nodeConfig.getAssigneeType())) {
            throw new IllegalArgumentException("流程节点审批人类型不能为空: " + nodeConfig.getNodeName());
        }
        return nodeConfig;
    }

    private String newId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }
}
