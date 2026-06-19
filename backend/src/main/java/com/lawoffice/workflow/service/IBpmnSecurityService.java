package com.lawoffice.workflow.service;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.workflow.vo.ProcessModelVO;

/**
 * BPMN 设计安全校验服务。
 */
public interface IBpmnSecurityService {

    /**
     * 校验 BPMN XML 是否符合审批中心二期白名单。
     *
     * @param bpmnXml BPMN XML 内容
     * @return 校验通过摘要
     */
    String validateBpmnXml(String bpmnXml);

    /**
     * 校验流程模型 BPMN XML 是否符合审批中心允许的元素和扩展范围。
     *
     * @param processModelId 流程模型ID
     * @param context 请求上下文
     * @return 更新后的流程模型安全校验摘要
     */
    BaseResult<ProcessModelVO> validateModel(String processModelId, RequestContext context);
}
