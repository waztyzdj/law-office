package com.lawoffice.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.framework.entity.BaseTenantEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_process_node_config")
@ModuleInfo(value = "workflow:node", name = "流程节点配置", description = "审批中心流程节点配置")
@Schema(description = "审批中心流程节点配置")
public class ProcessNodeConfig extends BaseTenantEntity {

    private String processModelId;

    private String nodeId;

    private String nodeName;

    private String nodeType;

    private String assigneeType;

    private String assigneeJson;

    private Integer allowTransfer;

    private Integer allowAddSign;

    private Integer allowReturn;

    private Integer sortOrder;
}
