package com.lawoffice.workflow.service;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.system.vo.FileUploadVO;
import com.lawoffice.workflow.dto.WorkflowDownloadFile;
import com.lawoffice.workflow.req.AdminMonitorPageReq;
import com.lawoffice.workflow.req.ArchiveActionReq;
import com.lawoffice.workflow.req.ArchivePageReq;
import com.lawoffice.workflow.vo.AdminMonitorDetailVO;
import com.lawoffice.workflow.vo.AttachmentVO;
import com.lawoffice.workflow.vo.ArchiveRecordVO;
import com.lawoffice.workflow.vo.ArchiveTreeNodeVO;
import com.lawoffice.workflow.vo.InstanceDiagramVO;

import java.io.InputStream;
import java.util.List;

/**
 * 流程归档业务服务。
 */
public interface IArchiveService {

    /**
     * 查询流程归档左侧流程树。
     *
     * @param context 请求上下文
     * @return 全部流程、流程分类和最新已发布流程定义树
     */
    BaseResult<List<ArchiveTreeNodeVO>> tree(RequestContext context);

    /**
     * 分页查询已归档流程实例。
     *
     * @param req 查询条件
     * @param context 请求上下文
     * @return 已归档记录分页
     */
    BaseResult<PageVO<ArchiveRecordVO>> pageArchived(ArchivePageReq req, RequestContext context);

    /**
     * 分页查询可人工归档的终止流程实例。
     * <p>
     * 已通过、已拒绝、已终止且尚未归档的实例进入未归档池，撤回、运行中、草稿和已归档实例均不返回。
     *
     * @param req 查询条件
     * @param context 请求上下文
     * @return 未归档已结束实例分页
     */
    BaseResult<PageVO<ArchiveRecordVO>> pageUnarchived(ArchivePageReq req, RequestContext context);

    /**
     * 查询流程归档详情。
     * <p>
     * 已归档实例和已终止未归档实例均允许从归档菜单查看详情，其它状态不允许通过归档入口查看。
     *
     * @param processInstanceId 流程实例 ID
     * @param context 请求上下文
     * @return 审批详情
     */
    BaseResult<AdminMonitorDetailVO> detail(String processInstanceId, RequestContext context);

    /**
     * 查询流程归档详情页使用的流程图数据。
     *
     * @param processInstanceId 流程实例 ID
     * @param context 请求上下文
     * @return 流程图数据
     */
    BaseResult<InstanceDiagramVO> diagram(String processInstanceId, RequestContext context);

    /**
     * 查询流程归档详情页可查看的审批附件。
     *
     * @param processInstanceId 流程实例 ID
     * @param context 请求上下文
     * @return 附件列表
     */
    BaseResult<List<AttachmentVO>> listAttachments(String processInstanceId, RequestContext context);

    /**
     * 按归档查看权限获取审批附件文件元数据。
     *
     * @param attachmentId 审批附件 ID
     * @param context 请求上下文
     * @return 文件元数据
     */
    FileUploadVO requireAttachmentFile(String attachmentId, RequestContext context);

    /**
     * 按归档查看权限下载审批附件内容。
     *
     * @param attachmentId 审批附件 ID
     * @param context 请求上下文
     * @return 文件内容流
     */
    InputStream downloadAttachmentContent(String attachmentId, RequestContext context);

    /**
     * 按归档查看权限打包下载流程实例附件，不包含审批 PDF。
     *
     * @param processInstanceId 流程实例 ID
     * @param context 请求上下文
     * @return 附件 ZIP 文件
     */
    WorkflowDownloadFile downloadAttachmentPackage(String processInstanceId, RequestContext context);

    /**
     * 正常结束流程自动归档。
     * <p>
     * 该方法用于流程运行时结束路径，要求实例状态已经落为自然结束状态；方法自身保持幂等，
     * 已存在归档记录时直接返回现有记录。
     *
     * @param processInstanceId 流程实例 ID
     * @param context 请求上下文，系统自动归档时可为空
     * @return 归档记录
     */
    BaseResult<ArchiveRecordVO> archiveAutomatically(String processInstanceId, RequestContext context);

    /**
     * 从流程监控入口手动归档已结束且未归档流程实例。
     *
     * @param req 归档请求
     * @param context 请求上下文
     * @return 归档记录
     */
    BaseResult<ArchiveRecordVO> archiveFromMonitor(ArchiveActionReq req, RequestContext context);

    /**
     * 从流程监控入口批量归档勾选的已结束且未归档流程实例。
     *
     * @param req 批量归档请求
     * @param context 请求上下文
     * @return 已归档记录
     */
    BaseResult<List<ArchiveRecordVO>> batchArchiveFromMonitor(ArchiveActionReq req, RequestContext context);

    /**
     * 从流程监控入口按当前查询条件批量归档已结束且未归档流程实例。
     *
     * @param req 流程监控查询条件
     * @param context 请求上下文
     * @return 已归档记录
     */
    BaseResult<List<ArchiveRecordVO>> batchArchiveByQueryFromMonitor(AdminMonitorPageReq req, RequestContext context);

    /**
     * 从流程归档菜单手动归档已结束且未归档流程实例。
     *
     * @param req 归档请求
     * @param context 请求上下文
     * @return 归档记录
     */
    BaseResult<ArchiveRecordVO> archiveFromArchiveMenu(ArchiveActionReq req, RequestContext context);

    /**
     * 从流程归档菜单批量归档已勾选的已结束未归档流程实例。
     * <p>
     * 批量归档只处理请求中明确传入的实例 ID，不做跨页隐式处理。
     *
     * @param req 批量归档请求
     * @param context 请求上下文
     * @return 已归档记录
     */
    BaseResult<List<ArchiveRecordVO>> batchArchiveFromArchiveMenu(ArchiveActionReq req, RequestContext context);

    /**
     * 从流程归档菜单按查询条件批量归档已结束且未归档的流程实例。
     * <p>
     * 查询条件复用未归档列表的边界，只处理通过、拒绝、终止等已结束状态，
     * 不处理运行中、草稿和撤回实例。
     *
     * @param req 查询条件
     * @param context 请求上下文
     * @return 已归档记录
     */
    BaseResult<List<ArchiveRecordVO>> batchArchiveByQueryFromArchiveMenu(ArchivePageReq req, RequestContext context);

    /**
     * 下载已归档流程的审批材料包。
     *
     * @param processInstanceId 流程实例 ID
     * @param context 请求上下文
     * @return 审批单 PDF 和原附件 ZIP 包
     */
    WorkflowDownloadFile downloadPackage(String processInstanceId, RequestContext context);

    /**
     * 判断流程实例是否已有有效归档记录。
     *
     * @param processInstanceId 流程实例 ID
     * @param tenantId 租户 ID
     * @return true 表示已归档
     */
    boolean isArchived(String processInstanceId, String tenantId);
}
