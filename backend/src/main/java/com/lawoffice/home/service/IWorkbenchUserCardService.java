package com.lawoffice.home.service;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.service.IBaseService;
import com.lawoffice.home.entity.WorkbenchUserCard;
import com.lawoffice.home.req.WorkbenchCardDataReq;
import com.lawoffice.home.req.WorkbenchLayoutSaveReq;
import com.lawoffice.home.vo.WorkbenchCardDataVO;
import com.lawoffice.home.vo.WorkbenchLayoutVO;
import com.lawoffice.home.vo.WorkbenchUserCardVO;

public interface IWorkbenchUserCardService extends IBaseService<WorkbenchUserCard, WorkbenchUserCardVO> {

    /**
     * 查询当前用户的工作台布局，将管理员卡片配置与用户个性化偏好合并。
     *
     * @param context 当前请求上下文，用于解析租户和用户
     * @return 当前用户可见卡片和已隐藏卡片
     */
    WorkbenchLayoutVO getCurrentLayout(RequestContext context);

    /**
     * 保存当前用户的工作台卡片显隐、栅格位置、尺寸和用户级配置。
     *
     * @param req 布局保存请求
     * @param context 当前请求上下文，用于审计、租户和用户隔离
     */
    void saveCurrentLayout(WorkbenchLayoutSaveReq req, RequestContext context);

    /**
     * 恢复当前用户的默认工作台布局。
     *
     * @param context 当前请求上下文，用于租户和用户隔离
     */
    void resetCurrentLayout(RequestContext context);

    /**
     * 查询单张工作台卡片数据。第一期先完成卡片启用状态和权限校验，具体业务数据由后续 Provider 扩展。
     *
     * @param req 卡片数据请求
     * @param context 当前请求上下文，用于租户、用户和权限校验
     * @return 卡片数据
     */
    WorkbenchCardDataVO getCardData(WorkbenchCardDataReq req, RequestContext context);
}
