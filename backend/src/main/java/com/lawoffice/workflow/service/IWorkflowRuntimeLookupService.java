package com.lawoffice.workflow.service;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.workflow.entity.FieldPermission;
import com.lawoffice.workflow.entity.FormDefinition;
import com.lawoffice.workflow.entity.FormInstance;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.ProcessModel;
import com.lawoffice.workflow.entity.Task;

import java.util.List;

/**
 * 工作流运行时通用查询与上下文校验服务。
 */
public interface IWorkflowRuntimeLookupService {

    /**
     * 校验并获取当前请求租户ID。
     *
     * @param context 请求上下文
     * @return 租户ID
     */
    String requireTenantId(RequestContext context);

    /**
     * 校验并获取当前请求用户ID。
     *
     * @param context 请求上下文
     * @return 用户ID
     */
    String requireUserId(RequestContext context);

    /**
     * 查询待处理任务，不存在或已处理时抛出业务参数异常。
     *
     * @param taskId 任务ID
     * @param tenantId 租户ID
     * @return 待处理任务
     */
    Task requireTodoTask(String taskId, String tenantId);

    /**
     * 查询审批实例，不存在时抛出业务参数异常。
     *
     * @param processInstanceId 审批实例ID
     * @param tenantId 租户ID
     * @return 审批实例
     */
    ProcessInstance requireProcessInstance(String processInstanceId, String tenantId);

    /**
     * 查询表单实例，不存在时抛出业务参数异常。
     *
     * @param formInstanceId 表单实例ID
     * @param tenantId 租户ID
     * @return 表单实例
     */
    FormInstance requireFormInstance(String formInstanceId, String tenantId);

    /**
     * 查询流程节点字段权限。
     *
     * @param processModelId 流程模型版本ID
     * @param nodeId 节点ID
     * @param tenantId 租户ID
     * @return 字段权限列表
     */
    List<FieldPermission> listFieldPermissions(String processModelId, String nodeId, String tenantId);

    /**
     * 查询已发布流程模型，不存在、未部署或不是最新发布版本时抛出业务参数异常。
     *
     * @param processModelId 流程模型版本ID
     * @param tenantId 租户ID
     * @return 已发布流程模型
     */
    ProcessModel requirePublishedModel(String processModelId, String tenantId);

    /**
     * 查询已发布表单定义，不存在时抛出业务参数异常。
     *
     * @param formDefinitionId 表单定义版本ID
     * @param tenantId 租户ID
     * @return 已发布表单定义
     */
    FormDefinition requirePublishedForm(String formDefinitionId, String tenantId);

    /**
     * 校验当前用户是否具备流程发起权限。
     *
     * @param model 流程模型
     * @param context 请求上下文
     */
    void checkStartPermission(ProcessModel model, RequestContext context);
}
