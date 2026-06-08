package com.lawoffice.workflow.service;

import com.lawoffice.framework.service.IBaseService;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.workflow.entity.ProcessModel;
import com.lawoffice.workflow.vo.ProcessModelVO;

/**
 * 审批流程模型版本服务。
 */
public interface IProcessModelService extends IBaseService<ProcessModel, ProcessModelVO> {

    /**
     * 发布流程模型版本。发布前部署 BPMN XML 到 Flowable，并保存部署和流程定义标识。
     *
     * @param id 流程模型ID
     * @param context 当前请求上下文
     * @return 发布后的流程模型
     */
    BaseResult<ProcessModelVO> publish(String id, RequestContext context);

    /**
     * 基于已有流程模型复制一个下一版本草稿，并复制节点配置、字段权限和发起权限。
     *
     * @param id 来源流程模型ID
     * @param context 当前请求上下文
     * @return 新草稿版本
     */
    BaseResult<ProcessModelVO> copyAsDraft(String id, RequestContext context);
}
