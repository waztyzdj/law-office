package com.lawoffice.workflow.service;

import com.lawoffice.framework.service.IBaseService;
import com.lawoffice.framework.dto.BasePageDTO;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.workflow.entity.ProcessModel;
import com.lawoffice.workflow.req.ProcessTemplateCopyReq;
import com.lawoffice.workflow.vo.ProcessModelVO;

import java.util.List;

/**
 * 审批流程模型版本服务。
 */
public interface IProcessModelService extends IBaseService<ProcessModel, ProcessModelVO> {

    /**
     * 分页查询每个流程标识下的最新模型版本，用于流程设计主列表。
     *
     * @param basePageDTO 分页、筛选和当前请求上下文
     * @return 最新版本分页结果
     */
    BaseResult<PageVO<ProcessModelVO>> pageLatest(BasePageDTO<ProcessModel> basePageDTO);

    /**
     * 查询指定流程模型所属流程标识下的全部历史版本，按版本号倒序返回。
     *
     * @param id 流程模型ID
     * @param context 当前请求上下文
     * @return 历史版本列表
     */
    BaseResult<List<ProcessModelVO>> listHistory(String id, RequestContext context);

    /**
     * 发布流程模型版本。发布前部署 BPMN XML 到 Flowable，并保存部署和流程定义标识。
     *
     * @param id 流程模型ID
     * @param context 当前请求上下文
     * @return 发布后的流程模型
     */
    BaseResult<ProcessModelVO> publish(String id, RequestContext context);

    /**
     * 基于已有流程模型复制一个下一版本草稿，并复制节点配置、字段权限和发起权限。
     *
     * @param id 来源流程模型ID
     * @param context 当前请求上下文
     * @return 新草稿版本
     */
    BaseResult<ProcessModelVO> copyAsDraft(String id, RequestContext context);

    /**
     * 基于已有流程复制为新的审批模板。新流程为独立草稿，版本号从 1 开始；
     * 只复制定义侧配置，不复制运行时实例、任务、记录、附件和消息。
     * 绑定来源表单时复制字段权限，改绑其他表单时跳过字段权限，避免字段不匹配。
     *
     * @param req 复制模板请求，包含来源流程、新流程编码名称以及绑定表单
     * @param context 当前请求上下文
     * @return 新复制出的流程模板草稿
     */
    BaseResult<ProcessModelVO> copyTemplate(ProcessTemplateCopyReq req, RequestContext context);
}
