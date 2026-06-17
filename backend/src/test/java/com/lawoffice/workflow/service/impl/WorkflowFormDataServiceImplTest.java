package com.lawoffice.workflow.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.FieldPermission;
import com.lawoffice.workflow.entity.FormInstance;
import com.lawoffice.workflow.mapper.FormInstanceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WorkflowFormDataServiceImplTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private FormInstanceMapper formInstanceMapper;

    private WorkflowFormDataServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new WorkflowFormDataServiceImpl(formInstanceMapper);
    }

    @Test
    void shouldTreatUnconfiguredStartFieldsAsEditable() throws Exception {
        FormInstance formInstance = formInstance("{}");

        service.saveStartFormData("{\"title\":\"申请\",\"amount\":100}", formInstance,
                List.of(), context(), true);

        assertUpdatedFormData("{\"title\":\"申请\",\"amount\":100}");
    }

    @Test
    void shouldTreatUnconfiguredRuntimeFieldsAsReadonly() throws Exception {
        FormInstance formInstance = formInstance("{\"title\":\"原值\",\"amount\":100}");

        service.saveRuntimeFormData("{\"title\":\"篡改\",\"amount\":200}", formInstance,
                List.of(), context(), true);

        assertUpdatedFormData("{\"title\":\"原值\",\"amount\":100}");
    }

    @Test
    void shouldOnlyMergeExplicitEditableRuntimeFields() throws Exception {
        FormInstance formInstance = formInstance("{\"title\":\"原值\",\"amount\":100,\"remark\":\"旧备注\"}");

        service.saveRuntimeFormData("{\"title\":\"篡改\",\"amount\":200,\"remark\":\"新备注\"}", formInstance,
                List.of(
                        permission("amount", WorkflowConstants.FieldPermission.EDITABLE),
                        permission("remark", WorkflowConstants.FieldPermission.READONLY)
                ), context(), true);

        assertUpdatedFormData("{\"title\":\"原值\",\"amount\":200,\"remark\":\"旧备注\"}");
    }

    @Test
    void shouldHonorReadonlyAndHiddenOverridesForStartFields() throws Exception {
        FormInstance formInstance = formInstance("{\"secret\":\"原隐藏值\"}");

        service.saveStartFormData("{\"title\":\"申请\",\"secret\":\"篡改隐藏值\",\"readonly\":\"篡改只读值\"}",
                formInstance,
                List.of(
                        permission("secret", WorkflowConstants.FieldPermission.HIDDEN),
                        permission("readonly", WorkflowConstants.FieldPermission.READONLY)
                ), context(), true);

        assertUpdatedFormData("{\"secret\":\"原隐藏值\",\"title\":\"申请\"}");
    }

    private void assertUpdatedFormData(String expectedJson) throws Exception {
        ArgumentCaptor<FormInstance> captor = ArgumentCaptor.forClass(FormInstance.class);
        verify(formInstanceMapper).updateById(captor.capture());
        JsonNode expected = OBJECT_MAPPER.readTree(expectedJson);
        JsonNode actual = OBJECT_MAPPER.readTree(captor.getValue().getFormDataJson());
        assertEquals(expected, actual);
    }

    private FormInstance formInstance(String formDataJson) {
        FormInstance formInstance = new FormInstance();
        formInstance.setId("form-instance-1");
        formInstance.setTenantId("tenant-1");
        formInstance.setFormDataJson(formDataJson);
        return formInstance;
    }

    private FieldPermission permission(String fieldKey, String permission) {
        FieldPermission fieldPermission = new FieldPermission();
        fieldPermission.setFieldKey(fieldKey);
        fieldPermission.setPermission(permission);
        fieldPermission.setRequiredFlag(0);
        return fieldPermission;
    }

    private RequestContext context() {
        return RequestContext.builder()
                .tenantId("tenant-1")
                .userId("user-1")
                .username("tester")
                .build();
    }
}
