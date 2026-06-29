package com.lawoffice.workflow.service;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.workflow.req.AdminMonitorActionReq;
import com.lawoffice.workflow.req.AdminMonitorPageReq;
import com.lawoffice.workflow.vo.AdminMonitorDetailVO;
import com.lawoffice.workflow.vo.AdminMonitorInstanceVO;
import com.lawoffice.workflow.vo.AdminOperationRecordVO;

import java.util.List;

/**
 * 流程监控管理服务。
 */
public interface IAdminMonitorService {

    /**
     * 分页查询流程实例监控列表。
     *
     * @param req 查询条件
     * @param context 请求上下文
     * @return 流程实例监控分页
     */
    BaseResult<PageVO<AdminMonitorInstanceVO>> page(AdminMonitorPageReq req, RequestContext context);

    /**
     * 查询流程监控详情，管理员查看不复用普通运行时访问权。
     *
     * @param processInstanceId 流程实例ID
     * @param context 请求上下文
     * @return 流程详情和维护记录
     */
    BaseResult<AdminMonitorDetailVO> detail(String processInstanceId, RequestContext context);

    /**
     * 将当前待办改派给指定用户。
     *
     * @param req 改派请求
     * @param context 请求上下文
     * @return 维护记录
     */
    BaseResult<AdminOperationRecordVO> reassign(AdminMonitorActionReq req, RequestContext context);

    /**
     * 管理员终止未结束流程实例。
     *
     * @param req 终止请求
     * @param context 请求上下文
     * @return 维护记录
     */
    BaseResult<AdminOperationRecordVO> terminate(AdminMonitorActionReq req, RequestContext context);

    /**
     * 补发当前有效待办通知，不新增任务也不改变流程状态。
     *
     * @param req 补发通知请求
     * @param context 请求上下文
     * @return 维护记录
     */
    BaseResult<AdminOperationRecordVO> resendNotice(AdminMonitorActionReq req, RequestContext context);

    /**
     * 查询指定流程实例的管理员维护记录。
     *
     * @param processInstanceId 流程实例ID
     * @param context 请求上下文
     * @return 维护记录列表
     */
    BaseResult<List<AdminOperationRecordVO>> listOperationRecords(String processInstanceId, RequestContext context);
}
