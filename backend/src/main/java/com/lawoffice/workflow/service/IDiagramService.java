package com.lawoffice.workflow.service;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.workflow.vo.InstanceDiagramVO;

/**
 * 审批流程图运行时服务。
 */
public interface IDiagramService {

    /**
     * 组装流程实例图谱数据，包含 BPMN XML、分支命中和操作记录。
     *
     * @param processInstanceId 流程实例ID
     * @param context 请求上下文
     * @return 流程图数据
     */
    BaseResult<InstanceDiagramVO> getInstanceDiagram(String processInstanceId, RequestContext context);

    /**
     * 在调用方已经完成业务入口鉴权后组装流程实例图谱数据。
     * <p>
     * 该方法不再校验普通运行时实例访问权，适用于流程监控、流程归档等拥有独立权限边界的只读入口。
     *
     * @param processInstanceId 流程实例ID
     * @param context 请求上下文
     * @return 流程图数据
     */
    BaseResult<InstanceDiagramVO> getInstanceDiagramForGrantedAccess(String processInstanceId, RequestContext context);
}
