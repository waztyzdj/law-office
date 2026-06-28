package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lawoffice.document.dto.BusinessDocumentAccessContext;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.ProcessModel;
import com.lawoffice.workflow.mapper.ProcessInstanceMapper;
import com.lawoffice.workflow.mapper.ProcessModelMapper;
import com.lawoffice.workflow.service.IRuntimeAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkflowBusinessDocumentProviderTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String INSTANCE_ID = "instance-1";
    private static final String PROCESS_MODEL_ID = "model-1";
    private static final BusinessDocumentAccessContext ACCESS_CONTEXT =
            new BusinessDocumentAccessContext("starter", "starter-1", TENANT_ID, List.of(), List.of());

    @Mock
    private ProcessInstanceMapper processInstanceMapper;
    @Mock
    private ProcessModelMapper processModelMapper;
    @Mock
    private IRuntimeAccessService runtimeAccessService;

    private WorkflowBusinessDocumentProvider provider;

    @BeforeEach
    void setUp() {
        provider = new WorkflowBusinessDocumentProvider(
                processInstanceMapper,
                processModelMapper,
                runtimeAccessService
        );
    }

    @Test
    void shouldExposeWorkflowApprovalBizTypeAndModuleName() {
        assertEquals(WorkflowConstants.BusinessDocument.APPROVAL_BIZ_TYPE, provider.bizType());
        assertEquals("审批中心", provider.moduleName());
    }

    @Test
    void shouldResolveProcessNameAsGroupName() {
        ProcessModel model = new ProcessModel();
        model.setId(PROCESS_MODEL_ID);
        model.setProcessName("合同审批");
        when(processModelMapper.selectList(any(Wrapper.class))).thenReturn(List.of(model));

        Map<String, String> names = provider.resolveGroupNames(List.of(PROCESS_MODEL_ID), ACCESS_CONTEXT);

        assertEquals("合同审批", names.get(PROCESS_MODEL_ID));
    }

    @Test
    void shouldResolveInstanceGroupIdAndFolderNameWithInstanceNo() {
        when(processInstanceMapper.selectList(any(Wrapper.class))).thenReturn(List.of(instance("HT-001")));

        Map<String, String> groupIds = provider.resolveRecordGroupIds(List.of(INSTANCE_ID), ACCESS_CONTEXT);
        Map<String, String> recordNames = provider.resolveRecordNames(List.of(INSTANCE_ID), ACCESS_CONTEXT);

        assertEquals(PROCESS_MODEL_ID, groupIds.get(INSTANCE_ID));
        assertEquals("采购合同审批-HT-001", recordNames.get(INSTANCE_ID));
    }

    @Test
    void shouldUseStartTimeWhenInstanceNoMissing() {
        ProcessInstance instance = instance(null);
        instance.setStartTime(LocalDateTime.of(2026, 6, 28, 10, 30, 5));
        when(processInstanceMapper.selectList(any(Wrapper.class))).thenReturn(List.of(instance));

        Map<String, String> recordNames = provider.resolveRecordNames(List.of(INSTANCE_ID), ACCESS_CONTEXT);

        assertEquals("采购合同审批-20260628103005", recordNames.get(INSTANCE_ID));
    }

    @Test
    void shouldReturnAccessResultFromRuntimeAccessService() {
        when(processInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(instance("HT-001"));

        assertTrue(provider.canAccess(INSTANCE_ID, ACCESS_CONTEXT));
    }

    @Test
    void shouldRejectWhenRuntimeAccessRejects() {
        when(processInstanceMapper.selectOne(any(Wrapper.class))).thenReturn(instance("HT-001"));
        doThrow(new IllegalArgumentException("无权"))
                .when(runtimeAccessService).ensureInstanceAccess(any(), any());

        assertFalse(provider.canAccess(INSTANCE_ID, ACCESS_CONTEXT));
    }

    private ProcessInstance instance(String instanceNo) {
        ProcessInstance instance = new ProcessInstance();
        instance.setId(INSTANCE_ID);
        instance.setTenantId(TENANT_ID);
        instance.setProcessModelId(PROCESS_MODEL_ID);
        instance.setInstanceTitle("采购合同审批");
        instance.setInstanceNo(instanceNo);
        instance.setStartTime(LocalDateTime.of(2026, 6, 28, 10, 30));
        return instance;
    }
}
