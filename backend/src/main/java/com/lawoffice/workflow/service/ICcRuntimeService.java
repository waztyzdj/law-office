package com.lawoffice.workflow.service;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.workflow.req.CcPageReq;
import com.lawoffice.workflow.vo.CcRecordVO;

/**
 * 审批抄送运行时服务。
 */
public interface ICcRuntimeService {

    /**
     * 分页查询当前用户收到的抄送记录。
     *
     * @param req 查询请求
     * @param context 请求上下文
     * @return 抄送记录分页
     */
    BaseResult<PageVO<CcRecordVO>> pageMine(CcPageReq req, RequestContext context);

    /**
     * 将当前用户收到的一条抄送记录标记为已读。
     *
     * @param ccRecordId 抄送记录ID
     * @param context 请求上下文
     * @return 标记后的抄送记录
     */
    BaseResult<CcRecordVO> markRead(String ccRecordId, RequestContext context);
}
