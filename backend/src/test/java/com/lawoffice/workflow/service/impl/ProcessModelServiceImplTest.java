package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.FieldPermission;
import com.lawoffice.workflow.entity.FormDefinition;
import com.lawoffice.workflow.entity.ProcessCategory;
import com.lawoffice.workflow.entity.ProcessModel;
import com.lawoffice.workflow.entity.ProcessNodeConfig;
import com.lawoffice.workflow.entity.ProcessStartPermission;
import com.lawoffice.workflow.mapper.FieldPermissionMapper;
import com.lawoffice.workflow.mapper.FormDefinitionMapper;
import com.lawoffice.workflow.mapper.ProcessCategoryMapper;
import com.lawoffice.workflow.mapper.ProcessInstanceMapper;
import com.lawoffice.workflow.mapper.ProcessModelMapper;
import com.lawoffice.workflow.mapper.ProcessNodeConfigMapper;
import com.lawoffice.workflow.mapper.ProcessStartPermissionMapper;
import com.lawoffice.workflow.req.ProcessTemplateCopyReq;
import com.lawoffice.workflow.service.IBpmnSecurityService;
import com.lawoffice.workflow.service.IFlowableService;
import com.lawoffice.workflow.vo.ProcessModelVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessModelServiceImplTest {

    private static final String TENANT_ID = "tenant-1";
    private static final String SOURCE_MODEL_ID = "process-source";
    private static final String SOURCE_FORM_ID = "form-source";
    private static final String TARGET_FORM_ID = "form-target";
    private static final String CATEGORY_ID = "category-1";

    @Mock
    private ProcessCategoryMapper processCategoryMapper;
    @Mock
    private FormDefinitionMapper formDefinitionMapper;
    @Mock
    private ProcessInstanceMapper processInstanceMapper;
    @Mock
    private ProcessModelMapper processModelMapper;
    @Mock
    private ProcessNodeConfigMapper processNodeConfigMapper;
    @Mock
    private FieldPermissionMapper fieldPermissionMapper;
    @Mock
    private ProcessStartPermissionMapper processStartPermissionMapper;
    @Mock
    private IFlowableService flowableService;
    @Mock
    private IBpmnSecurityService bpmnSecurityService;

    private ProcessModelServiceImpl service;
    private RequestContext context;

    @BeforeEach
    void setUp() {
        service = new ProcessModelServiceImpl(
                processCategoryMapper,
                formDefinitionMapper,
                processInstanceMapper,
                processNodeConfigMapper,
                fieldPermissionMapper,
                processStartPermissionMapper,
                flowableService,
                bpmnSecurityService
        );
        ReflectionTestUtils.setField(service, "baseMapper", processModelMapper);
        context = RequestContext.builder()
                .tenantId(TENANT_ID)
                .userId("admin-1")
                .username("admin")
                .build();
    }

    @Test
    void shouldNotCopyFieldPermissionsWhenTemplateCopyChangesBoundForm() {
        ProcessModel sourceModel = sourceModel();
        FormDefinition sourceForm = formDefinition(SOURCE_FORM_ID, "source_form", "来源表单");
        FormDefinition targetForm = formDefinition(TARGET_FORM_ID, "target_form", "目标表单");
        when(processModelMapper.selectOne(any(Wrapper.class))).thenReturn(sourceModel);
        when(formDefinitionMapper.selectOne(any(Wrapper.class))).thenReturn(sourceForm, targetForm, targetForm);
        when(processCategoryMapper.selectOne(any(Wrapper.class))).thenReturn(processCategory());
        when(processModelMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(processNodeConfigMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(processStartPermissionMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        BaseResult<ProcessModelVO> result = service.copyTemplate(copyReq(), context);

        assertEquals(200, result.getCode(), result.getMessage());
        assertNotNull(result.getData());
        assertEquals(TARGET_FORM_ID, result.getData().getFormDefinitionId());
        assertEquals("target_form", result.getData().getFormKey());
        assertEquals("目标表单", result.getData().getFormName());
        verify(fieldPermissionMapper, never()).selectList(any(Wrapper.class));
        verify(fieldPermissionMapper, never()).insert(any(FieldPermission.class));

        ArgumentCaptor<ProcessModel> processCaptor = ArgumentCaptor.forClass(ProcessModel.class);
        verify(processModelMapper).insert(processCaptor.capture());
        ProcessModel copied = processCaptor.getValue();
        assertEquals(TARGET_FORM_ID, copied.getFormDefinitionId());
        assertEquals(1, copied.getVersion());
        assertEquals(WorkflowConstants.Status.DRAFT, copied.getStatus());
    }

    private ProcessTemplateCopyReq copyReq() {
        ProcessTemplateCopyReq req = new ProcessTemplateCopyReq();
        req.setSourceProcessModelId(SOURCE_MODEL_ID);
        req.setCategoryId(CATEGORY_ID);
        req.setFormDefinitionId(TARGET_FORM_ID);
        req.setProcessKey("target_process");
        req.setProcessName("目标流程");
        return req;
    }

    private ProcessModel sourceModel() {
        ProcessModel model = new ProcessModel();
        model.setId(SOURCE_MODEL_ID);
        model.setTenantId(TENANT_ID);
        model.setCategoryId(CATEGORY_ID);
        model.setFormDefinitionId(SOURCE_FORM_ID);
        model.setProcessKey("source_process");
        model.setProcessName("来源流程");
        model.setDesignerType(WorkflowConstants.DesignerType.SIMPLE);
        model.setStatus(WorkflowConstants.Status.PUBLISHED);
        model.setVersion(3);
        model.setBpmnXml(null);
        return model;
    }

    private FormDefinition formDefinition(String id, String key, String name) {
        FormDefinition form = new FormDefinition();
        form.setId(id);
        form.setTenantId(TENANT_ID);
        form.setFormKey(key);
        form.setFormName(name);
        form.setVersion(1);
        form.setStatus(WorkflowConstants.Status.PUBLISHED);
        return form;
    }

    private ProcessCategory processCategory() {
        ProcessCategory category = new ProcessCategory();
        category.setId(CATEGORY_ID);
        category.setTenantId(TENANT_ID);
        category.setCategoryName("业务审批");
        return category;
    }
}
