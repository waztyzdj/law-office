package com.lawoffice.home.service;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.home.entity.WorkbenchCard;
import com.lawoffice.home.req.WorkbenchCardDataReq;
import com.lawoffice.home.vo.WorkbenchCardDataVO;

/**
 * 工作台卡片数据扩展点。
 */
public interface IWorkbenchCardDataProvider {

    /**
     * 判断当前 Provider 是否支持指定卡片编码。
     *
     * @param cardCode 卡片编码
     * @return 是否支持
     */
    boolean supports(String cardCode);

    /**
     * 加载卡片数据。调用前工作台主服务已完成卡片启用状态和卡片权限校验。
     *
     * @param req 卡片数据请求
     * @param card 卡片配置
     * @param context 当前请求上下文
     * @return 卡片数据
     */
    WorkbenchCardDataVO loadData(WorkbenchCardDataReq req, WorkbenchCard card, RequestContext context);
}
