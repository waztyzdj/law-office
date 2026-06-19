package com.lawoffice.workflow.service;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.workflow.req.AttachmentBindReq;
import com.lawoffice.workflow.vo.AttachmentVO;

import java.util.List;

/**
 * 审批附件运行时服务。
 */
public interface IAttachmentRuntimeService {

    /**
     * 查询流程实例下当前用户可见的审批附件。
     *
     * @param processInstanceId 流程实例ID
     * @param context 请求上下文
     * @return 附件列表
     */
    BaseResult<List<AttachmentVO>> listByInstance(String processInstanceId, RequestContext context);

    /**
     * 绑定已上传文件为审批附件。
     *
     * @param req 附件绑定请求
     * @param context 请求上下文
     * @return 附件业务记录
     */
    BaseResult<AttachmentVO> bind(AttachmentBindReq req, RequestContext context);

    /**
     * 删除审批附件业务记录，并保留与文件中心解绑定的业务边界。
     *
     * @param attachmentId 审批附件ID
     * @param context 请求上下文
     * @return 删除结果
     */
    BaseResult<Void> delete(String attachmentId, RequestContext context);
}
