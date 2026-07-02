package com.lawoffice.workflow.service;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.workflow.dto.WorkflowDownloadFile;

/**
 * 审批实例下载服务。
 */
public interface IWorkflowDownloadService {

    /**
     * 下载已通过结束的审批实例材料包，包含审批单 PDF 和原始附件。
     *
     * @param processInstanceId 流程实例ID
     * @param context 请求上下文
     * @return ZIP 文件
     */
    WorkflowDownloadFile downloadPackage(String processInstanceId, RequestContext context);

    /**
     * 下载流程归档材料包。
     * <p>
     * 该入口由流程归档权限控制，不复用普通运行时访问权；允许已归档的通过、不通过和终止实例下载。
     *
     * @param processInstanceId 流程实例 ID
     * @param context 请求上下文
     * @return ZIP 材料包
     */
    WorkflowDownloadFile downloadArchivePackage(String processInstanceId, RequestContext context);
}
