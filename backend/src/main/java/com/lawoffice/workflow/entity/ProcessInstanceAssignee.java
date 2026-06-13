package com.lawoffice.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.framework.entity.BaseTenantEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_process_instance_assignee")
@ModuleInfo(value = "workflow:instance-assignee", name = "流程实例审批人快照", description = "审批中心流程实例节点审批人快照")
@Schema(description = "审批中心流程实例节点审批人快照")
public class ProcessInstanceAssignee extends BaseTenantEntity {

    private String processInstanceId;

    private String processModelId;

    private String nodeId;

    private String nodeName;

    private String assigneeType;

    private String assigneeUserId;

    private String assigneeUsername;

    private String assigneeRealname;

    private String sourceType;

    private String sourceId;

    private String selectType;

    private String status;
}
