package com.lawoffice.workflow.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.util.EntityFillUtils;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.FieldPermission;
import com.lawoffice.workflow.entity.FormInstance;
import com.lawoffice.workflow.mapper.FormInstanceMapper;
import com.lawoffice.workflow.service.IWorkflowFormDataService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class WorkflowFormDataServiceImpl implements IWorkflowFormDataService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final FormInstanceMapper formInstanceMapper;

    public WorkflowFormDataServiceImpl(FormInstanceMapper formInstanceMapper) {
        this.formInstanceMapper = formInstanceMapper;
    }

    @Override
    public void saveStartFormData(String formDataJson, FormInstance formInstance,
            List<FieldPermission> permissions, RequestContext context, boolean validateRequired) {
        if (!StringUtils.hasText(formDataJson)) {
            return;
        }
        saveRuntimeFormData(formDataJson, formInstance, permissions, context, validateRequired);
    }

    @Override
    public void saveRuntimeFormData(String formDataJson, FormInstance formInstance,
            List<FieldPermission> permissions, RequestContext context, boolean validateRequired) {
        try {
            JsonNode submitted = OBJECT_MAPPER.readTree(formDataJson);
            JsonNode current = StringUtils.hasText(formInstance.getFormDataJson())
                    ? OBJECT_MAPPER.readTree(formInstance.getFormDataJson()) : OBJECT_MAPPER.createObjectNode();
            if (!submitted.isObject() || !current.isObject()) {
                throw new IllegalArgumentException("表单数据必须是JSON对象");
            }
            if (permissions == null || permissions.isEmpty()) {
                if (validateRequired) {
                    validateRequiredFields(List.of(), (ObjectNode) submitted);
                }
                formInstance.setFormDataJson(OBJECT_MAPPER.writeValueAsString(submitted));
                EntityFillUtils.fillAuditFields(formInstance, context, false);
                formInstanceMapper.updateById(formInstance);
                return;
            }
            ObjectNode merged = ((ObjectNode) current).deepCopy();
            Set<String> editableFields = new HashSet<>();
            for (FieldPermission permission : permissions) {
                if (WorkflowConstants.FieldPermission.EDITABLE.equals(permission.getPermission())) {
                    editableFields.add(permission.getFieldKey());
                }
            }
            for (String fieldKey : editableFields) {
                if (submitted.has(fieldKey)) {
                    merged.set(fieldKey, submitted.get(fieldKey));
                }
            }
            if (validateRequired) {
                validateRequiredFields(permissions, merged);
            }
            formInstance.setFormDataJson(OBJECT_MAPPER.writeValueAsString(merged));
            EntityFillUtils.fillAuditFields(formInstance, context, false);
            formInstanceMapper.updateById(formInstance);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("表单数据JSON处理失败");
        }
    }

    private void validateRequiredFields(List<FieldPermission> permissions, ObjectNode formData) {
        for (FieldPermission permission : permissions) {
            if (Integer.valueOf(1).equals(permission.getRequiredFlag())
                    && WorkflowConstants.FieldPermission.EDITABLE.equals(permission.getPermission())
                    && isEmptyJsonValue(formData.get(permission.getFieldKey()))) {
                throw new IllegalArgumentException("必填字段不能为空: " + permission.getFieldKey());
            }
        }
    }

    private boolean isEmptyJsonValue(JsonNode node) {
        return node == null || node.isNull()
                || (node.isTextual() && !StringUtils.hasText(node.asText()))
                || (node.isArray() && node.isEmpty());
    }
}
