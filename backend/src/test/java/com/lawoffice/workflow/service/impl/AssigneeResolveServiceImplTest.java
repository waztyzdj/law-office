package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.system.entity.DepartRole;
import com.lawoffice.system.entity.DepartRoleUser;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.entity.UserDepart;
import com.lawoffice.system.entity.UserTenant;
import com.lawoffice.system.mapper.DepartRoleMapper;
import com.lawoffice.system.mapper.DepartRoleUserMapper;
import com.lawoffice.system.mapper.UserDepartMapper;
import com.lawoffice.system.mapper.UserMapper;
import com.lawoffice.system.mapper.UserRoleMapper;
import com.lawoffice.system.mapper.UserTenantMapper;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.dto.FlowableTaskInfo;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.ProcessNodeConfig;
import com.lawoffice.workflow.entity.Task;
import com.lawoffice.workflow.mapper.ProcessInstanceAssigneeMapper;
import com.lawoffice.workflow.mapper.ProcessModelMapper;
import com.lawoffice.workflow.mapper.ProcessNodeConfigMapper;
import com.lawoffice.workflow.mapper.TaskCandidateMapper;
import com.lawoffice.workflow.mapper.TaskMapper;
import com.lawoffice.workflow.service.IFlowableService;
import com.lawoffice.workflow.service.IInstanceStateService;
import com.lawoffice.workflow.vo.AssigneeSelectNodeVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssigneeResolveServiceImplTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String PROCESS_MODEL_ID = "model-1";
    private static final String PROCESS_INSTANCE_ID = "instance-1";
    private static final String FLOWABLE_INSTANCE_ID = "flowable-instance-1";
    private static final String FLOWABLE_TASK_ID = "flowable-task-1";
    private static final String STARTER_ID = "starter-1";
    private static final String SUPERVISOR_ID = "supervisor-1";

    @Mock
    private DepartRoleUserMapper departRoleUserMapper;
    @Mock
    private DepartRoleMapper departRoleMapper;
    @Mock
    private IFlowableService flowableService;
    @Mock
    private IInstanceStateService instanceStateService;
    @Mock
    private ProcessInstanceAssigneeMapper processInstanceAssigneeMapper;
    @Mock
    private ProcessModelMapper processModelMapper;
    @Mock
    private ProcessNodeConfigMapper processNodeConfigMapper;
    @Mock
    private TaskCandidateMapper taskCandidateMapper;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private UserDepartMapper userDepartMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private UserRoleMapper userRoleMapper;
    @Mock
    private UserTenantMapper userTenantMapper;

    private AssigneeResolveServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AssigneeResolveServiceImpl(
                departRoleUserMapper,
                departRoleMapper,
                flowableService,
                instanceStateService,
                processInstanceAssigneeMapper,
                processModelMapper,
                processNodeConfigMapper,
                taskCandidateMapper,
                taskMapper,
                userDepartMapper,
                userMapper,
                userRoleMapper,
                userTenantMapper
        );
    }

    @Test
    void shouldResolveStarterSupervisorWhenSyncCurrentTasks() {
        ProcessInstance processInstance = processInstance();
        when(flowableService.listActiveTasks(FLOWABLE_INSTANCE_ID))
                .thenReturn(List.of(new FlowableTaskInfo(FLOWABLE_TASK_ID, "approve_1", "直属上级审批", null, null)));
        when(processNodeConfigMapper.selectOne(any(Wrapper.class))).thenReturn(starterSupervisorNode());
        when(processInstanceAssigneeMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(userDepartMapper.selectList(any(Wrapper.class))).thenReturn(List.of(starterDepart()));
        when(userTenantMapper.selectList(any(Wrapper.class))).thenReturn(List.of(userTenant()));
        when(userMapper.selectList(any(Wrapper.class))).thenReturn(List.of(supervisor()));

        service.syncCurrentTasks(processInstance, TENANT_ID, context());

        verify(flowableService).setTaskAssignee(FLOWABLE_TASK_ID, SUPERVISOR_ID);
        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper).insert(taskCaptor.capture());
        Task task = taskCaptor.getValue();
        assertEquals(SUPERVISOR_ID, task.getAssigneeUserId());
        assertEquals("supervisor", task.getAssigneeUsername());
        assertEquals("直属上级", task.getAssigneeRealname());
        assertEquals(WorkflowConstants.Status.TODO, task.getStatus());
        verify(instanceStateService).refreshCurrentTaskSummary(processInstance, TENANT_ID);
    }

    @Test
    void shouldFallbackToStarterSelectWhenStarterSupervisorMissing() {
        ProcessInstance processInstance = processInstance();
        when(processModelMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(processNodeConfigMapper.selectList(any(Wrapper.class))).thenReturn(List.of(starterSupervisorNode()));
        when(userDepartMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        List<AssigneeSelectNodeVO> nodes = service.buildRequiredAssigneeSelectNodes(
                PROCESS_MODEL_ID,
                processInstance,
                TENANT_ID,
                WorkflowConstants.VirtualNode.START_DRAFT);

        assertEquals(1, nodes.size());
        AssigneeSelectNodeVO node = nodes.get(0);
        assertEquals("approve_1", node.getNodeId());
        assertEquals("直属上级审批", node.getNodeName());
        assertEquals(WorkflowConstants.AssigneeType.STARTER_SELECT, node.getAssigneeType());
        assertEquals(Boolean.TRUE, node.getFallback());
        assertEquals("下一节点未解析到审批人，请手动选择下一审批人", node.getWarningMessage());
        assertEquals(List.of(), node.getOptions());
    }

    @Test
    void shouldFallbackToStarterSelectWhenAnySupportedAssigneeTypeResolvesEmpty() {
        List<ProcessNodeConfig> missingNodes = new ArrayList<>();
        missingNodes.add(node("approve_user", "指定人员审批", WorkflowConstants.AssigneeType.USER,
                "{\"userIds\":[\"missing-user\"]}"));
        missingNodes.add(node("approve_role", "指定角色审批", WorkflowConstants.AssigneeType.ROLE,
                "{\"roleIds\":[\"missing-role\"]}"));
        missingNodes.add(node("approve_depart_leader", "部门负责人审批", WorkflowConstants.AssigneeType.DEPART_LEADER, null));
        missingNodes.add(node("approve_depart_role", "部门岗位审批", WorkflowConstants.AssigneeType.DEPART_ROLE,
                "{\"departRoleIds\":[\"missing-depart-role\"]}"));
        missingNodes.add(starterSupervisorNode());
        missingNodes.add(node("approve_starter", "发起人本人审批", WorkflowConstants.AssigneeType.STARTER, null));

        for (ProcessNodeConfig missingNode : missingNodes) {
            assertFallbackToStarterSelect(missingNode);
        }
    }

    @Test
    void shouldRequireExplicitSelectionForUserNodesWithMultipleAssignees() {
        ProcessInstance processInstance = processInstance();
        when(processModelMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(processNodeConfigMapper.selectList(any(Wrapper.class))).thenReturn(List.of(node(
                "approve_user",
                "指定人员审批",
                WorkflowConstants.AssigneeType.USER,
                "{\"userIds\":[\"user-1\",\"user-2\"]}"
        )));
        when(userTenantMapper.selectList(any(Wrapper.class))).thenReturn(List.of(userTenant("user-1"), userTenant("user-2")));
        when(userMapper.selectList(any(Wrapper.class))).thenReturn(List.of(user("user-1", "u1", "张三"), user("user-2", "u2", "李四")));

        List<AssigneeSelectNodeVO> nodes = service.buildRequiredAssigneeSelectNodes(
                PROCESS_MODEL_ID,
                processInstance,
                TENANT_ID,
                WorkflowConstants.VirtualNode.START_DRAFT);

        assertEquals(1, nodes.size());
        AssigneeSelectNodeVO node = nodes.get(0);
        assertEquals(WorkflowConstants.AssigneeType.USER, node.getAssigneeType());
        assertEquals(2, node.getOptions().size());
        assertEquals("user-1", node.getOptions().get(0).getUserId());
        assertEquals("user-2", node.getOptions().get(1).getUserId());
    }

    @Test
    void shouldCreateCountersignTasksForEachResolvedAssignee() {
        ProcessInstance processInstance = processInstance();
        ProcessNodeConfig nodeConfig = node(
                "approve_user",
                "会签审批",
                WorkflowConstants.AssigneeType.USER,
                "{\"userIds\":[\"user-1\",\"user-2\"]}"
        );
        nodeConfig.setApprovalMode(WorkflowConstants.ApprovalMode.COUNTERSIGN);
        nodeConfig.setAssigneeResolveMode(WorkflowConstants.AssigneeResolveMode.ALL);
        when(flowableService.listActiveTasks(FLOWABLE_INSTANCE_ID))
                .thenReturn(List.of(new FlowableTaskInfo(FLOWABLE_TASK_ID, "approve_user", "会签审批", null, null)));
        when(processNodeConfigMapper.selectOne(any(Wrapper.class))).thenReturn(nodeConfig);
        when(processInstanceAssigneeMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(userTenantMapper.selectList(any(Wrapper.class))).thenReturn(List.of(userTenant("user-1"), userTenant("user-2")));
        when(userMapper.selectList(any(Wrapper.class))).thenReturn(List.of(user("user-1", "u1", "张三"), user("user-2", "u2", "李四")));

        service.syncCurrentTasks(processInstance, TENANT_ID, context());

        verify(flowableService).addCandidateUsers(eq(FLOWABLE_TASK_ID), eq(List.of("user-1", "user-2")));
        ArgumentCaptor<Task> taskCaptor = ArgumentCaptor.forClass(Task.class);
        verify(taskMapper, times(2)).insert(taskCaptor.capture());
        List<Task> tasks = taskCaptor.getAllValues();
        assertEquals(2, tasks.size());
        assertEquals(WorkflowConstants.TaskType.COUNTERSIGN, tasks.get(0).getTaskType());
        assertEquals(WorkflowConstants.ApprovalMode.COUNTERSIGN, tasks.get(0).getApprovalMode());
        assertEquals(2, tasks.get(0).getGroupTotal());
        assertEquals(0, tasks.get(0).getGroupCompleted());
        assertEquals(FLOWABLE_TASK_ID, tasks.get(0).getFlowableTaskId());
        assertEquals(tasks.get(0).getTaskGroupId(), tasks.get(1).getTaskGroupId());
        assertEquals(tasks.get(0).getId(), tasks.get(1).getParentTaskId());
        assertEquals("group:" + tasks.get(1).getId(), tasks.get(1).getFlowableTaskId());
        assertEquals("user-1", tasks.get(0).getAssigneeUserId());
        assertEquals("user-2", tasks.get(1).getAssigneeUserId());
        verify(instanceStateService).refreshCurrentTaskSummary(processInstance, TENANT_ID);
    }

    @Test
    void shouldRequireMultipleSelectionForCountersignWhenResolveModeSelect() {
        ProcessInstance processInstance = processInstance();
        ProcessNodeConfig nodeConfig = node(
                "approve_user",
                "会签审批",
                WorkflowConstants.AssigneeType.USER,
                "{\"userIds\":[\"user-1\",\"user-2\"]}"
        );
        nodeConfig.setApprovalMode(WorkflowConstants.ApprovalMode.COUNTERSIGN);
        nodeConfig.setAssigneeResolveMode(WorkflowConstants.AssigneeResolveMode.SELECT);
        when(processModelMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(processNodeConfigMapper.selectList(any(Wrapper.class))).thenReturn(List.of(nodeConfig));
        when(userTenantMapper.selectList(any(Wrapper.class))).thenReturn(List.of(userTenant("user-1"), userTenant("user-2")));
        when(userMapper.selectList(any(Wrapper.class))).thenReturn(List.of(user("user-1", "u1", "张三"), user("user-2", "u2", "李四")));

        List<AssigneeSelectNodeVO> nodes = service.buildRequiredAssigneeSelectNodes(
                PROCESS_MODEL_ID,
                processInstance,
                TENANT_ID,
                WorkflowConstants.VirtualNode.START_DRAFT);

        assertEquals(1, nodes.size());
        assertEquals("multiple", nodes.get(0).getSelectType());
        assertEquals(2, nodes.get(0).getOptions().size());
    }

    @Test
    void shouldSkipSelectionForCountersignWhenResolveModeAll() {
        ProcessInstance processInstance = processInstance();
        ProcessNodeConfig nodeConfig = node(
                "approve_user",
                "会签审批",
                WorkflowConstants.AssigneeType.USER,
                "{\"userIds\":[\"user-1\",\"user-2\"]}"
        );
        nodeConfig.setApprovalMode(WorkflowConstants.ApprovalMode.COUNTERSIGN);
        nodeConfig.setAssigneeResolveMode(WorkflowConstants.AssigneeResolveMode.ALL);
        when(processModelMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(processNodeConfigMapper.selectList(any(Wrapper.class))).thenReturn(List.of(nodeConfig));
        when(userTenantMapper.selectList(any(Wrapper.class))).thenReturn(List.of(userTenant("user-1"), userTenant("user-2")));
        when(userMapper.selectList(any(Wrapper.class))).thenReturn(List.of(user("user-1", "u1", "张三"), user("user-2", "u2", "李四")));

        List<AssigneeSelectNodeVO> nodes = service.buildRequiredAssigneeSelectNodes(
                PROCESS_MODEL_ID,
                processInstance,
                TENANT_ID,
                WorkflowConstants.VirtualNode.START_DRAFT);

        assertEquals(0, nodes.size());
    }

    @Test
    void shouldOnlyResolveWorkflowEnabledDepartRoles() {
        ProcessInstance processInstance = processInstance();
        when(processModelMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(processNodeConfigMapper.selectList(any(Wrapper.class))).thenReturn(List.of(node(
                "approve_depart_role",
                "部门岗位审批",
                WorkflowConstants.AssigneeType.DEPART_ROLE,
                "{\"departRoleIds\":[\"workflow-role\",\"normal-role\"]}"
        )));
        when(departRoleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(departRole("workflow-role")));
        DepartRoleUser enabledUser = departRoleUser("workflow-role", "user-1");
        DepartRoleUser disabledUser = departRoleUser("normal-role", "user-2");
        when(departRoleUserMapper.selectList(any(Wrapper.class))).thenReturn(List.of(enabledUser, disabledUser));
        when(userTenantMapper.selectList(any(Wrapper.class))).thenReturn(List.of(userTenant("user-1")));
        when(userMapper.selectList(any(Wrapper.class))).thenReturn(List.of(user("user-1", "u1", "张三")));

        List<AssigneeSelectNodeVO> nodes = service.buildRequiredAssigneeSelectNodes(
                PROCESS_MODEL_ID,
                processInstance,
                TENANT_ID,
                WorkflowConstants.VirtualNode.START_DRAFT);

        assertEquals(0, nodes.size());
    }

    private void assertFallbackToStarterSelect(ProcessNodeConfig missingNode) {
        reset(processModelMapper, processNodeConfigMapper, userDepartMapper, userTenantMapper,
                userRoleMapper, departRoleUserMapper, departRoleMapper, userMapper);
        ProcessInstance processInstance = processInstance();
        when(processModelMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(processNodeConfigMapper.selectList(any(Wrapper.class))).thenReturn(List.of(missingNode));
        mockEmptyResolverResult(missingNode.getAssigneeType());

        List<AssigneeSelectNodeVO> nodes = service.buildRequiredAssigneeSelectNodes(
                PROCESS_MODEL_ID,
                processInstance,
                TENANT_ID,
                WorkflowConstants.VirtualNode.START_DRAFT);

        assertEquals(1, nodes.size(), missingNode.getAssigneeType());
        AssigneeSelectNodeVO node = nodes.get(0);
        assertEquals(missingNode.getNodeId(), node.getNodeId());
        assertEquals(WorkflowConstants.AssigneeType.STARTER_SELECT, node.getAssigneeType());
        assertEquals(Boolean.TRUE, node.getFallback());
        assertEquals(List.of(), node.getOptions());
    }

    private void mockEmptyResolverResult(String assigneeType) {
        if (WorkflowConstants.AssigneeType.USER.equals(assigneeType)
                || WorkflowConstants.AssigneeType.STARTER.equals(assigneeType)) {
            when(userTenantMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
            return;
        }
        if (WorkflowConstants.AssigneeType.ROLE.equals(assigneeType)) {
            when(userRoleMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
            return;
        }
        if (WorkflowConstants.AssigneeType.DEPART_LEADER.equals(assigneeType)
                || WorkflowConstants.AssigneeType.STARTER_SUPERVISOR.equals(assigneeType)) {
            when(userDepartMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
            return;
        }
        if (WorkflowConstants.AssigneeType.DEPART_ROLE.equals(assigneeType)) {
            when(departRoleMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
            return;
        }
    }

    private DepartRole departRole(String id) {
        DepartRole departRole = new DepartRole();
        departRole.setId(id);
        departRole.setWorkflowEnabled(1);
        return departRole;
    }

    private DepartRoleUser departRoleUser(String droleId, String userId) {
        DepartRoleUser roleUser = new DepartRoleUser();
        roleUser.setDroleId(droleId);
        roleUser.setUserId(userId);
        roleUser.setTenantId(TENANT_ID);
        return roleUser;
    }

    private ProcessInstance processInstance() {
        ProcessInstance processInstance = new ProcessInstance();
        processInstance.setId(PROCESS_INSTANCE_ID);
        processInstance.setTenantId(TENANT_ID);
        processInstance.setProcessModelId(PROCESS_MODEL_ID);
        processInstance.setFlowableProcessInstanceId(FLOWABLE_INSTANCE_ID);
        processInstance.setStarterUserId(STARTER_ID);
        return processInstance;
    }

    private ProcessNodeConfig node(String nodeId, String nodeName, String assigneeType, String assigneeJson) {
        ProcessNodeConfig nodeConfig = new ProcessNodeConfig();
        nodeConfig.setNodeId(nodeId);
        nodeConfig.setNodeName(nodeName);
        nodeConfig.setAssigneeType(assigneeType);
        nodeConfig.setAssigneeJson(assigneeJson);
        return nodeConfig;
    }

    private ProcessNodeConfig starterSupervisorNode() {
        ProcessNodeConfig nodeConfig = new ProcessNodeConfig();
        nodeConfig.setNodeId("approve_1");
        nodeConfig.setNodeName("直属上级审批");
        nodeConfig.setAssigneeType(WorkflowConstants.AssigneeType.STARTER_SUPERVISOR);
        return nodeConfig;
    }

    private UserDepart starterDepart() {
        UserDepart userDepart = new UserDepart();
        userDepart.setUserId(STARTER_ID);
        userDepart.setDepId("depart-1");
        userDepart.setPrimaryDepartFlag(1);
        userDepart.setSupervisorUserId(SUPERVISOR_ID);
        return userDepart;
    }

    private UserTenant userTenant() {
        return userTenant(SUPERVISOR_ID);
    }

    private UserTenant userTenant(String userId) {
        UserTenant userTenant = new UserTenant();
        userTenant.setUserId(userId);
        userTenant.setTenantId(TENANT_ID);
        userTenant.setStatus("1");
        return userTenant;
    }

    private User supervisor() {
        return user(SUPERVISOR_ID, "supervisor", "直属上级");
    }

    private User user(String id, String username, String realname) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setRealname(realname);
        user.setStatus(1);
        return user;
    }

    private RequestContext context() {
        return RequestContext.builder()
                .tenantId(TENANT_ID)
                .userId(STARTER_ID)
                .username("starter")
                .build();
    }
}
