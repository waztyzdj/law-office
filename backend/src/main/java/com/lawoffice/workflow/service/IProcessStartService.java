package com.lawoffice.workflow.service;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.workflow.req.StartProcessReq;
import com.lawoffice.workflow.vo.StartProcessVO;

/**
 * 审批发起流程服务。
 */
public interface IProcessStartService {

    /**
     * 发起审批申请，写入业务实例并启动 Flowable 流程。
     *
     * @param req 发起申请请求
     * @param context 当前请求上下文
     * @return 发起结果
     */
    BaseResult<StartProcessVO> start(StartProcessReq req, RequestContext context);

    /**
     * 保存发起申请草稿，并生成发起人的待提交任务。
     *
     * @param req 发起申请草稿请求
     * @param context 当前请求上下文
     * @return 草稿实例结果
     */
    BaseResult<StartProcessVO> saveStartDraft(StartProcessReq req, RequestContext context);
}
