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
}
