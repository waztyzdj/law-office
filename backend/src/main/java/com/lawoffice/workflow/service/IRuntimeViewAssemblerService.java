package com.lawoffice.workflow.service;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.workflow.entity.FieldPermission;
import com.lawoffice.workflow.entity.FormDefinition;
import com.lawoffice.workflow.entity.FormInstance;
import com.lawoffice.workflow.entity.OperationRecord;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.ProcessModel;
import com.lawoffice.workflow.entity.ProcessNodeConfig;
import com.lawoffice.workflow.entity.Task;
import com.lawoffice.workflow.vo.AvailableProcessVO;
import com.lawoffice.workflow.vo.InstanceDetailVO;
import com.lawoffice.workflow.vo.OperationRecordVO;
import com.lawoffice.workflow.vo.RuntimeTaskVO;
import com.lawoffice.workflow.vo.StartFormVO;
import com.lawoffice.workflow.vo.StartedInstanceVO;
import com.lawoffice.workflow.vo.TaskFormVO;

import java.util.List;

/**
 * 运行时读模型组装服务。
 */
public interface IRuntimeViewAssemblerService {

    /**
     * 组装可发起流程列表。
     *
     * @param processModels 流程模型列表
     * @param tenantId 租户ID
     * @return 可发起流程VO列表
     */
    List<AvailableProcessVO> buildAvailableProcessRecords(List<ProcessModel> processModels, String tenantId);

    /**
     * 组装我发起的审批列表。
     *
     * @param instances 流程实例列表
     * @param tenantId 租户ID
     * @return 我发起的审批VO列表
     */
    List<StartedInstanceVO> buildStartedInstanceRecords(List<ProcessInstance> instances, String tenantId);

    /**
     * 组装运行时任务列表。
     *
     * @param tasks 任务列表
     * @param tenantId 租户ID
     * @return 运行时任务VO列表
     */
    List<RuntimeTaskVO> buildRuntimeTaskRecords(List<Task> tasks, String tenantId);

    /**
     * 组装审批实例详情。
     *
     * @param processInstance 流程实例
     * @param formInstance 表单实例
     * @param currentTasks 当前任务列表
     * @param records 操作记录列表
     * @return 审批实例详情
     */
    InstanceDetailVO buildInstanceDetail(ProcessInstance processInstance, FormInstance formInstance,
            List<Task> currentTasks, List<OperationRecord> records);

    /**
     * 组装操作记录VO。
     *
     * @param record 操作记录
     * @return 操作记录VO
     */
    OperationRecordVO buildOperationRecordVO(OperationRecord record);

    /**
     * 组装任务办理表单。
     *
     * @param task 任务
     * @param processInstance 流程实例
     * @param formInstance 表单实例
     * @param permissions 字段权限
     * @param nodeConfig 节点配置
     * @return 任务办理表单
     */
    TaskFormVO buildTaskForm(Task task, ProcessInstance processInstance, FormInstance formInstance,
            List<FieldPermission> permissions, ProcessNodeConfig nodeConfig);

    /**
     * 组装发起表单。
     *
     * @param model 流程模型
     * @param form 表单定义
     * @param permissions 字段权限
     * @param context 请求上下文
     * @return 发起表单
     */
    StartFormVO buildStartForm(ProcessModel model, FormDefinition form,
            List<FieldPermission> permissions, RequestContext context);
}
