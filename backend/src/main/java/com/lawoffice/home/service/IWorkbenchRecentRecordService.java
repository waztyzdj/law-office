package com.lawoffice.home.service;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.service.IBaseService;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.home.entity.WorkbenchRecentRecord;
import com.lawoffice.home.req.WorkbenchRecentClearReq;
import com.lawoffice.home.req.WorkbenchRecentPageReq;
import com.lawoffice.home.req.WorkbenchRecentRecordReq;
import com.lawoffice.home.vo.WorkbenchRecentRecordVO;

public interface IWorkbenchRecentRecordService extends IBaseService<WorkbenchRecentRecord, WorkbenchRecentRecordVO> {

    /**
     * 分页查询当前用户当前租户下的近期工作记录。
     *
     * @param req 查询条件和分页参数
     * @param context 当前请求上下文，用于解析租户和用户
     * @return 近期工作分页结果
     */
    PageVO<WorkbenchRecentRecordVO> pageCurrentUserRecords(WorkbenchRecentPageReq req, RequestContext context);

    /**
     * 记录当前用户访问或打开的工作对象，并按记录合并键更新最近访问时间和访问次数。
     *
     * @param req 近期工作记录请求
     * @param context 当前请求上下文，用于审计、租户和用户隔离
     * @return 保存后的近期工作记录
     */
    WorkbenchRecentRecordVO recordCurrentUserVisit(WorkbenchRecentRecordReq req, RequestContext context);

    /**
     * 清空当前用户当前租户下的近期工作记录。
     *
     * @param req 清空请求，可按记录类型清空
     * @param context 当前请求上下文，用于租户和用户隔离
     */
    void clearCurrentUserRecords(WorkbenchRecentClearReq req, RequestContext context);
}
