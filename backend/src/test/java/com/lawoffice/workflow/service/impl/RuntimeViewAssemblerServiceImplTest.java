package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.FieldPermission;
import com.lawoffice.workflow.entity.FormInstance;
import com.lawoffice.workflow.entity.OperationRecord;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.ProcessModel;
import com.lawoffice.workflow.entity.ProcessNodeConfig;
import com.lawoffice.workflow.entity.Task;
import com.lawoffice.workflow.mapper.FormDefinitionMapper;
import com.lawoffice.workflow.mapper.FormInstanceMapper;
import com.lawoffice.workflow.mapper.OperationRecordMapper;
import com.lawoffice.workflow.mapper.ProcessInstanceMapper;
import com.lawoffice.workflow.mapper.ProcessModelMapper;
import com.lawoffice.workflow.mapper.ReminderRecordMapper;
import com.lawoffice.workflow.mapper.TaskCandidateMapper;
import com.lawoffice.workflow.mapper.TaskMapper;
import com.lawoffice.workflow.service.IAssigneeResolveService;
import com.lawoffice.workflow.service.IProcessNodeConfigService;
import com.lawoffice.workflow.vo.AssigneeSelectNodeVO;
import com.lawoffice.workflow.vo.RuntimeTaskVO;
import com.lawoffice.workflow.vo.StartedInstanceVO;
import com.lawoffice.workflow.vo.TaskFormVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RuntimeViewAssemblerServiceImplTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String PROCESS_MODEL_ID = "model-1";

    @Mock
    private ProcessModelMapper processModelMapper;
    @Mock
    private FormDefinitionMapper formDefinitionMapper;
    @Mock
    private FormInstanceMapper formInstanceMapper;
    @Mock
    private OperationRecordMapper operationRecordMapper;
    @Mock
    private ReminderRecordMapper reminderRecordMapper;
    @Mock
    private ProcessInstanceMapper processInstanceMapper;
    @Mock
    private TaskCandidateMapper taskCandidateMapper;
    @Mock
    private TaskMapper taskMapper;
    @Mock
    private IAssigneeResolveService assigneeResolveService;
    @Mock
    private IProcessNodeConfigService processNodeConfigService;

    private RuntimeViewAssemblerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RuntimeViewAssemblerServiceImpl(
                processModelMapper,
                formDefinitionMapper,
                formInstanceMapper,
                operationRecordMapper,
                processInstanceMapper,
                taskCandidateMapper,
                taskMapper,
                reminderRecordMapper,
                assigneeResolveService,
                processNodeConfigService
        );
    }

    @Test
    void shouldMarkStartedInstanceWithdrawableOnlyBeforeApproverHandled() {
        ProcessInstance processInstance = processInstance();
        ProcessModel processModel = new ProcessModel();
        processModel.setId(PROCESS_MODEL_ID);
        processModel.setProcessName("请假流程");
        OperationRecord handledRecord = new OperationRecord();
        handledRecord.setProcessInstanceId(processInstance.getId());
        handledRecord.setAction(WorkflowConstants.Action.APPROVE);

        when(processModelMapper.selectList(any(Wrapper.class))).thenReturn(List.of(processModel));
        when(formInstanceMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(operationRecordMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(taskMapper.selectList(any(Wrapper.class))).thenReturn(List.of(task(WorkflowConstants.TaskType.NORMAL, "approve_1")));
        when(reminderRecordMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        List<StartedInstanceVO> withdrawableRecords = service.buildStartedInstanceRecords(List.of(processInstance), TENANT_ID);

        assertTrue(withdrawableRecords.get(0).getCanWithdraw());
        assertTrue(withdrawableRecords.get(0).getCanUrge());

        when(operationRecordMapper.selectList(any(Wrapper.class))).thenReturn(List.of(handledRecord));
        List<StartedInstanceVO> blockedRecords = service.buildStartedInstanceRecords(List.of(processInstance), TENANT_ID);

        assertFalse(blockedRecords.get(0).getCanWithdraw());
    }

    @Test
    void shouldBuildNormalTaskFormWithActionPermissionsReturnNodesAndFieldPermissions() {
        Task task = task(WorkflowConstants.TaskType.NORMAL, "approve_2");
        ProcessInstance processInstance = processInstance();
        FormInstance formInstance = formInstance();
        ProcessNodeConfig nodeConfig = nodeConfig("approve_2", "二级审批", 1, 1, 1);
        ProcessNodeConfig returnNode = nodeConfig("approve_1", "一级审批", 1, 1, 1);
        FieldPermission permission = fieldPermission("reason", WorkflowConstants.FieldPermission.EDITABLE, 1);
        AssigneeSelectNodeVO selectNode = assigneeSelectNode("approve_3");

        when(processNodeConfigService.listReturnableNodeConfigs(processInstance, nodeConfig, TENANT_ID))
                .thenReturn(List.of(returnNode));
        when(assigneeResolveService.buildRequiredAssigneeSelectNodes(
                eq(PROCESS_MODEL_ID), same(processInstance), eq(TENANT_ID), eq("approve_2")))
                .thenReturn(List.of(selectNode));

        TaskFormVO vo = service.buildTaskForm(
                task, processInstance, formInstance, List.of(permission), nodeConfig);

        assertEquals(task.getId(), vo.getTaskId());
        assertEquals(processInstance.getId(), vo.getProcessInstanceId());
        assertEquals(formInstance.getFormDefinitionId(), vo.getFormDefinitionId());
        assertEquals(task.getApprovalMode(), vo.getApprovalMode());
        assertEquals(task.getTaskGroupId(), vo.getTaskGroupId());
        assertEquals(task.getGroupTotal(), vo.getGroupTotal());
        assertEquals(task.getGroupCompleted(), vo.getGroupCompleted());
        assertTrue(vo.getActionPermissions().getAllowApprove());
        assertTrue(vo.getActionPermissions().getAllowReject());
        assertTrue(vo.getActionPermissions().getAllowTransfer());
        assertTrue(vo.getActionPermissions().getAllowAddSign());
        assertTrue(vo.getActionPermissions().getAllowReturn());
        assertEquals(2, vo.getReturnNodes().size());
        assertEquals(WorkflowConstants.VirtualNode.START_DRAFT, vo.getReturnNodes().get(0).getNodeId());
        assertEquals("approve_1", vo.getReturnNodes().get(1).getNodeId());
        assertEquals("reason", vo.getFieldPermissions().get(0).getFieldKey());
        assertEquals(1, vo.getFieldPermissions().get(0).getRequiredFlag());
        assertEquals("approve_3", vo.getAssigneeSelectNodes().get(0).getNodeId());
    }

    @Test
    void shouldBuildStartDraftTaskFormWithOnlyApprovePermission() {
        Task task = task(WorkflowConstants.TaskType.START_DRAFT, WorkflowConstants.VirtualNode.START_DRAFT);
        ProcessInstance processInstance = processInstance();
        FormInstance formInstance = formInstance();
        ProcessNodeConfig nodeConfig = nodeConfig(WorkflowConstants.VirtualNode.START_DRAFT,
                WorkflowConstants.VirtualNodeName.START_DRAFT, 0, 0, 0);
        FieldPermission permission = fieldPermission("reason", WorkflowConstants.FieldPermission.EDITABLE, 1);

        when(assigneeResolveService.buildRequiredAssigneeSelectNodes(
                eq(PROCESS_MODEL_ID), same(processInstance), eq(TENANT_ID), eq(WorkflowConstants.VirtualNode.START_DRAFT)))
                .thenReturn(List.of());

        TaskFormVO vo = service.buildTaskForm(
                task, processInstance, formInstance, List.of(permission), nodeConfig);

        assertTrue(vo.getActionPermissions().getAllowApprove());
        assertFalse(vo.getActionPermissions().getAllowReject());
        assertFalse(vo.getActionPermissions().getAllowTransfer());
        assertFalse(vo.getActionPermissions().getAllowAddSign());
        assertFalse(vo.getActionPermissions().getAllowReturn());
        assertTrue(vo.getReturnNodes().isEmpty());
        verifyNoInteractions(processNodeConfigService);
    }

    @Test
    void shouldHideNextAssigneeSelectionBeforeCountersignLastApprover() {
        Task task = task(WorkflowConstants.TaskType.COUNTERSIGN, "approve_2");
        task.setApprovalMode(WorkflowConstants.ApprovalMode.COUNTERSIGN);
        task.setTaskGroupId("group-1");
        task.setGroupTotal(2);
        task.setGroupCompleted(0);
        ProcessInstance processInstance = processInstance();
        FormInstance formInstance = formInstance();
        ProcessNodeConfig nodeConfig = nodeConfig("approve_2", "会签审批", 1, 1, 1);
        FieldPermission permission = fieldPermission("reason", WorkflowConstants.FieldPermission.EDITABLE, 1);

        when(processNodeConfigService.listReturnableNodeConfigs(processInstance, nodeConfig, TENANT_ID))
                .thenReturn(List.of());
        when(taskMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        TaskFormVO vo = service.buildTaskForm(
                task, processInstance, formInstance, List.of(permission), nodeConfig);

        assertTrue(vo.getAssigneeSelectNodes().isEmpty());
        verify(assigneeResolveService, never()).buildRequiredAssigneeSelectNodes(
                any(), any(), any(), any());
    }

    @Test
    void shouldShowNextAssigneeSelectionForCountersignLastApprover() {
        Task task = task(WorkflowConstants.TaskType.COUNTERSIGN, "approve_2");
        task.setApprovalMode(WorkflowConstants.ApprovalMode.COUNTERSIGN);
        task.setTaskGroupId("group-1");
        task.setGroupTotal(2);
        task.setGroupCompleted(1);
        ProcessInstance processInstance = processInstance();
        FormInstance formInstance = formInstance();
        ProcessNodeConfig nodeConfig = nodeConfig("approve_2", "会签审批", 1, 1, 1);
        FieldPermission permission = fieldPermission("reason", WorkflowConstants.FieldPermission.EDITABLE, 1);
        AssigneeSelectNodeVO selectNode = assigneeSelectNode("approve_3");

        when(processNodeConfigService.listReturnableNodeConfigs(processInstance, nodeConfig, TENANT_ID))
                .thenReturn(List.of());
        when(taskMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        when(assigneeResolveService.buildRequiredAssigneeSelectNodes(
                eq(PROCESS_MODEL_ID), same(processInstance), eq(TENANT_ID), eq("approve_2")))
                .thenReturn(List.of(selectNode));

        TaskFormVO vo = service.buildTaskForm(
                task, processInstance, formInstance, List.of(permission), nodeConfig);

        assertEquals("approve_3", vo.getAssigneeSelectNodes().get(0).getNodeId());
    }

    @Test
    void shouldBuildRuntimeTaskRecordsWithInstanceSummary() {
        Task task = task(WorkflowConstants.TaskType.NORMAL, "approve_1");
        ProcessInstance processInstance = processInstance();
        when(processInstanceMapper.selectList(any(Wrapper.class))).thenReturn(List.of(processInstance));

        List<RuntimeTaskVO> records = service.buildRuntimeTaskRecords(List.of(task), TENANT_ID);

        assertEquals(1, records.size());
        RuntimeTaskVO vo = records.get(0);
        assertEquals(task.getId(), vo.getId());
        assertEquals(processInstance.getInstanceNo(), vo.getInstanceNo());
        assertEquals(processInstance.getInstanceTitle(), vo.getInstanceTitle());
        assertEquals(processInstance.getStarterUserId(), vo.getStarterUserId());
        assertEquals(processInstance.getStarterRealname(), vo.getStarterRealname());
    }

    private Task task(String taskType, String nodeId) {
        Task task = new Task();
        task.setId("task-1");
        task.setTenantId(TENANT_ID);
        task.setProcessInstanceId("instance-1");
        task.setFlowableTaskId("flowable-task-1");
        task.setNodeId(nodeId);
        task.setTaskName("审批");
        task.setTaskType(taskType);
        task.setApprovalMode(WorkflowConstants.ApprovalMode.SINGLE);
        task.setAssigneeUserId("assignee-1");
        task.setAssigneeUsername("assignee");
        task.setAssigneeRealname("审批人");
        task.setStatus(WorkflowConstants.Status.TODO);
        return task;
    }

    private ProcessInstance processInstance() {
        ProcessInstance processInstance = new ProcessInstance();
        processInstance.setId("instance-1");
        processInstance.setTenantId(TENANT_ID);
        processInstance.setProcessModelId(PROCESS_MODEL_ID);
        processInstance.setFormInstanceId("form-instance-1");
        processInstance.setInstanceNo("WF-001");
        processInstance.setInstanceTitle("测试审批");
        processInstance.setStarterUserId("starter-1");
        processInstance.setStarterUsername("starter");
        processInstance.setStarterRealname("发起人");
        processInstance.setStatus(WorkflowConstants.Status.RUNNING);
        return processInstance;
    }

    private FormInstance formInstance() {
        FormInstance formInstance = new FormInstance();
        formInstance.setId("form-instance-1");
        formInstance.setTenantId(TENANT_ID);
        formInstance.setFormDefinitionId("form-1");
        formInstance.setFormKey("test_form");
        formInstance.setFormName("测试表单");
        formInstance.setFormVersion(1);
        formInstance.setFormSchemaSnapshotJson("{}");
        formInstance.setFormOptionSnapshotJson("{}");
        formInstance.setFormDataJson("{\"reason\":\"test\"}");
        return formInstance;
    }

    private ProcessNodeConfig nodeConfig(String nodeId, String nodeName,
            Integer allowTransfer, Integer allowAddSign, Integer allowReturn) {
        ProcessNodeConfig nodeConfig = new ProcessNodeConfig();
        nodeConfig.setNodeId(nodeId);
        nodeConfig.setNodeName(nodeName);
        nodeConfig.setNodeType(WorkflowConstants.NodeType.APPROVER);
        nodeConfig.setAllowTransfer(allowTransfer);
        nodeConfig.setAllowAddSign(allowAddSign);
        nodeConfig.setAllowReturn(allowReturn);
        return nodeConfig;
    }

    private FieldPermission fieldPermission(String fieldKey, String permission, Integer requiredFlag) {
        FieldPermission fieldPermission = new FieldPermission();
        fieldPermission.setFieldKey(fieldKey);
        fieldPermission.setPermission(permission);
        fieldPermission.setRequiredFlag(requiredFlag);
        return fieldPermission;
    }

    private AssigneeSelectNodeVO assigneeSelectNode(String nodeId) {
        AssigneeSelectNodeVO vo = new AssigneeSelectNodeVO();
        vo.setNodeId(nodeId);
        vo.setNodeName("下一审批");
        vo.setRequired(true);
        return vo;
    }

}
