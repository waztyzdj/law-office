package com.lawoffice.workflow.service;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.workflow.entity.FieldPermission;
import com.lawoffice.workflow.entity.FormInstance;

import java.util.List;

/**
 * 工作流运行时表单数据保存服务。
 */
public interface IWorkflowFormDataService {

    /**
     * 保存发起环节表单数据。未配置字段权限时，发起节点默认可编辑。
     *
     * @param formDataJson 表单数据JSON
     * @param formInstance 表单实例
     * @param permissions 字段权限
     * @param context 请求上下文
     * @param validateRequired 是否校验必填字段
     */
    void saveStartFormData(String formDataJson, FormInstance formInstance,
            List<FieldPermission> permissions, RequestContext context, boolean validateRequired);

    /**
     * 保存运行时表单数据，并按字段权限合并可编辑字段。未配置字段权限时，审批节点默认只读。
     *
     * @param formDataJson 表单数据JSON
     * @param formInstance 表单实例
     * @param permissions 字段权限
     * @param context 请求上下文
     * @param validateRequired 是否校验必填字段
     */
    void saveRuntimeFormData(String formDataJson, FormInstance formInstance,
            List<FieldPermission> permissions, RequestContext context, boolean validateRequired);
}
