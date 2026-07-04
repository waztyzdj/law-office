package com.lawoffice.home.service;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.service.IBaseService;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.home.entity.WorkbenchCard;
import com.lawoffice.home.req.WorkbenchCardPageReq;
import com.lawoffice.home.req.WorkbenchCardReq;
import com.lawoffice.home.req.WorkbenchCardSortReq;
import com.lawoffice.home.vo.WorkbenchCardVO;

import java.util.List;

public interface IWorkbenchCardService extends IBaseService<WorkbenchCard, WorkbenchCardVO> {

    /**
     * 分页查询当前租户的工作台卡片配置。
     *
     * @param req 查询条件和分页参数
     * @param context 当前请求上下文，用于解析租户
     * @return 卡片配置分页结果
     */
    PageVO<WorkbenchCardVO> pageCards(WorkbenchCardPageReq req, RequestContext context);

    /**
     * 查询当前租户的一条工作台卡片配置。
     *
     * @param id 卡片配置 ID
     * @param context 当前请求上下文，用于解析租户
     * @return 卡片配置详情
     */
    WorkbenchCardVO getCardDetail(String id, RequestContext context);

    /**
     * 新增或编辑工作台卡片配置，并校验编码唯一、权限点和展示配置。
     *
     * @param req 卡片配置请求
     * @param context 当前请求上下文，用于审计和租户隔离
     * @return 保存后的卡片配置
     */
    WorkbenchCardVO saveCard(WorkbenchCardReq req, RequestContext context);

    /**
     * 启用或停用工作台卡片。
     *
     * @param id 卡片配置 ID
     * @param status 目标状态
     * @param context 当前请求上下文，用于审计和租户隔离
     */
    void updateStatus(String id, String status, RequestContext context);

    /**
     * 批量调整工作台卡片默认排序。
     *
     * @param req 排序请求
     * @param context 当前请求上下文，用于审计和租户隔离
     */
    void updateSort(WorkbenchCardSortReq req, RequestContext context);

    /**
     * 查询当前用户在当前租户下有权访问的启用卡片。
     *
     * @param context 当前请求上下文，用于解析租户和用户权限
     * @return 有权访问的启用卡片
     */
    List<WorkbenchCard> listAuthorizedEnabledCards(RequestContext context);

    /**
     * 校验当前用户是否有权访问指定启用卡片。
     *
     * @param cardCode 卡片编码
     * @param context 当前请求上下文，用于解析租户和用户权限
     * @return 有权访问的卡片配置
     */
    WorkbenchCard requireAuthorizedEnabledCard(String cardCode, RequestContext context);
}
