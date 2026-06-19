package com.lawoffice.workflow.service;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.workflow.req.BranchRecordReq;
import com.lawoffice.workflow.vo.BranchRecordVO;

import java.util.List;

/**
 * 审批条件分支运行时服务。
 */
public interface IBranchRuntimeService {

    /**
     * 记录一次条件分支命中结果。
     *
     * @param req 分支命中请求
     * @param context 请求上下文
     * @return 分支命中记录
     */
    BaseResult<BranchRecordVO> recordMatch(BranchRecordReq req, RequestContext context);

    /**
     * 查询流程实例的条件分支命中记录。
     *
     * @param processInstanceId 流程实例ID
     * @param context 请求上下文
     * @return 分支命中记录列表
     */
    BaseResult<List<BranchRecordVO>> listByInstance(String processInstanceId, RequestContext context);
}
