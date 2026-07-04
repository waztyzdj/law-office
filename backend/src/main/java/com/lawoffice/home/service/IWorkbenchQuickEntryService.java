package com.lawoffice.home.service;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.service.IBaseService;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.home.entity.WorkbenchQuickEntry;
import com.lawoffice.home.req.WorkbenchQuickEntryListReq;
import com.lawoffice.home.req.WorkbenchQuickEntryPageReq;
import com.lawoffice.home.req.WorkbenchQuickEntryReq;
import com.lawoffice.home.vo.WorkbenchQuickEntryListVO;
import com.lawoffice.home.vo.WorkbenchQuickEntryVO;

public interface IWorkbenchQuickEntryService extends IBaseService<WorkbenchQuickEntry, WorkbenchQuickEntryVO> {

    /**
     * 分页查询当前租户的系统默认快捷菜单。
     *
     * @param req 查询条件和分页参数
     * @param context 当前请求上下文，用于解析租户
     * @return 系统默认快捷菜单分页结果
     */
    PageVO<WorkbenchQuickEntryVO> pageSystemEntries(WorkbenchQuickEntryPageReq req, RequestContext context);

    /**
     * 保存管理员维护的系统默认快捷菜单。
     *
     * @param req 快捷菜单请求
     * @param context 当前请求上下文，用于审计和租户隔离
     * @return 保存后的快捷菜单
     */
    WorkbenchQuickEntryVO saveSystemEntry(WorkbenchQuickEntryReq req, RequestContext context);

    /**
     * 启用或停用系统默认快捷菜单。
     *
     * @param id 快捷菜单 ID
     * @param status 目标状态
     * @param context 当前请求上下文，用于审计和租户隔离
     */
    void updateSystemStatus(String id, String status, RequestContext context);

    /**
     * 查询当前用户可访问的快捷菜单，合并系统默认菜单和个人菜单。
     *
     * @param req 查询请求
     * @param context 当前请求上下文，用于解析租户、用户和权限
     * @return 当前用户快捷菜单列表
     */
    WorkbenchQuickEntryListVO listCurrentUserEntries(WorkbenchQuickEntryListReq req, RequestContext context);

    /**
     * 保存当前用户的个人快捷菜单。
     *
     * @param req 快捷菜单请求
     * @param context 当前请求上下文，用于审计、租户和用户隔离
     * @return 保存后的个人快捷菜单
     */
    WorkbenchQuickEntryVO saveCurrentUserEntry(WorkbenchQuickEntryReq req, RequestContext context);

    /**
     * 删除当前用户的个人快捷菜单。
     *
     * @param id 快捷菜单 ID
     * @param context 当前请求上下文，用于租户和用户隔离
     */
    void deleteCurrentUserEntry(String id, RequestContext context);
}
