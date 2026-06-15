package com.lawoffice.workflow.service;

import com.lawoffice.framework.service.IBaseService;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.ProcessNodeConfig;
import com.lawoffice.workflow.vo.ProcessNodeConfigVO;

import java.util.List;

/**
 * 审批流程节点配置服务。
 */
public interface IProcessNodeConfigService extends IBaseService<ProcessNodeConfig, ProcessNodeConfigVO> {

    /**
     * 查询运行时节点配置，节点必须属于当前租户和流程模型，且未逻辑删除。
     *
     * @param processModelId 流程模型ID
     * @param nodeId BPMN节点ID
     * @param tenantId 租户ID
     * @return 节点配置
     */
    ProcessNodeConfig requireRuntimeNodeConfig(String processModelId, String nodeId, String tenantId);

    /**
     * 构造退回发起人时使用的发起草稿虚拟节点配置。
     *
     * @return 发起草稿虚拟节点配置
     */
    ProcessNodeConfig buildStartDraftNodeConfig();

    /**
     * 查询当前任务允许退回的审批节点，规则与退回动作校验保持一致。
     *
     * @param processInstance 流程实例
     * @param currentNodeConfig 当前节点配置
     * @param tenantId 租户ID
     * @return 可退回节点列表
     */
    List<ProcessNodeConfig> listReturnableNodeConfigs(ProcessInstance processInstance,
            ProcessNodeConfig currentNodeConfig, String tenantId);

    /**
     * 校验退回目标节点是否允许。
     *
     * @param processInstance 流程实例
     * @param currentNodeConfig 当前节点配置
     * @param targetNodeConfig 目标节点配置
     * @param tenantId 租户ID
     */
    void ensureReturnTargetAllowed(ProcessInstance processInstance, ProcessNodeConfig currentNodeConfig,
            ProcessNodeConfig targetNodeConfig, String tenantId);
}
