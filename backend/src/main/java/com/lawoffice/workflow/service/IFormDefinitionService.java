package com.lawoffice.workflow.service;

import com.lawoffice.framework.service.IBaseService;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.workflow.entity.FormDefinition;
import com.lawoffice.workflow.vo.FormDefinitionVO;

/**
 * 审批表单定义版本服务。
 */
public interface IFormDefinitionService extends IBaseService<FormDefinition, FormDefinitionVO> {

    /**
     * 发布表单定义版本。发布后该版本不可直接修改，如需调整应复制为新草稿版本。
     *
     * @param id 表单定义ID
     * @param context 当前请求上下文
     * @return 发布后的表单定义
     */
    BaseResult<FormDefinitionVO> publish(String id, RequestContext context);

    /**
     * 基于已有表单定义复制一个下一版本草稿。
     *
     * @param id 来源表单定义ID
     * @param context 当前请求上下文
     * @return 新草稿版本
     */
    BaseResult<FormDefinitionVO> copyAsDraft(String id, RequestContext context);
}
