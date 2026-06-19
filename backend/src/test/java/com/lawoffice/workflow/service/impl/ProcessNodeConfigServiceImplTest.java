package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.ProcessModel;
import com.lawoffice.workflow.entity.ProcessNodeConfig;
import com.lawoffice.workflow.mapper.ProcessModelMapper;
import com.lawoffice.workflow.mapper.ProcessNodeConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessNodeConfigServiceImplTest {

    private static final String TENANT_ID = "tenant-1";

    @Mock
    private ProcessModelMapper processModelMapper;
    @Mock
    private ProcessNodeConfigMapper processNodeConfigMapper;

    private ProcessNodeConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProcessNodeConfigServiceImpl(processModelMapper);
        ReflectionTestUtils.setField(service, "baseMapper", processNodeConfigMapper);
        lenient().when(processModelMapper.selectOne(any(Wrapper.class))).thenReturn(draftModel());
    }

    @Test
    void shouldRejectInvalidApprovalMode() {
        ProcessNodeConfig config = baseApprover();
        config.setApprovalMode("invalid");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.doBeforeSave(saveDTO(config)));

        assertEquals("办理策略不合法", exception.getMessage());
    }

    @Test
    void shouldRejectGatewayWithoutDefaultBranch() {
        ProcessNodeConfig config = new ProcessNodeConfig();
        config.setTenantId(TENANT_ID);
        config.setProcessModelId("model-1");
        config.setNodeId("gateway-1");
        config.setNodeName("金额判断");
        config.setNodeType(WorkflowConstants.NodeType.GATEWAY);
        config.setBranchJson("""
                {"branches":[{"branchId":"high","targetNodeId":"manager"}]}
                """);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.doBeforeSave(saveDTO(config)));

        assertEquals("条件分支必须配置默认分支", exception.getMessage());
    }

    @Test
    void shouldRejectNonApproverAssigneeConfig() {
        ProcessNodeConfig config = new ProcessNodeConfig();
        config.setTenantId(TENANT_ID);
        config.setProcessModelId("model-1");
        config.setNodeId("start");
        config.setNodeName("开始");
        config.setNodeType(WorkflowConstants.NodeType.START);
        config.setAssigneeType(WorkflowConstants.AssigneeType.USER);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> service.doBeforeSave(saveDTO(config)));

        assertEquals("非审批节点不能配置审批人", exception.getMessage());
    }

    private ProcessNodeConfig baseApprover() {
        ProcessNodeConfig config = new ProcessNodeConfig();
        config.setTenantId(TENANT_ID);
        config.setProcessModelId("model-1");
        config.setNodeId("approve");
        config.setNodeName("审批");
        config.setNodeType(WorkflowConstants.NodeType.APPROVER);
        config.setAssigneeType(WorkflowConstants.AssigneeType.USER);
        config.setAssigneeJson("{\"userIds\":[\"user-1\"]}");
        return config;
    }

    private BaseDTO<ProcessNodeConfig> saveDTO(ProcessNodeConfig config) {
        BaseDTO<ProcessNodeConfig> dto = new BaseDTO<>();
        dto.setEntity(config);
        dto.setContext(RequestContext.builder().tenantId(TENANT_ID).username("tester").build());
        return dto;
    }

    private ProcessModel draftModel() {
        ProcessModel model = new ProcessModel();
        model.setId("model-1");
        model.setTenantId(TENANT_ID);
        model.setStatus(WorkflowConstants.Status.DRAFT);
        return model;
    }
}
