package com.lawoffice.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.framework.entity.BaseTenantEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_process_instance")
@ModuleInfo(value = "workflow:instance", name = "审批实例", description = "审批中心流程实例")
@Schema(description = "审批中心流程实例")
public class ProcessInstance extends BaseTenantEntity {

    private String processModelId;

    private String formInstanceId;

    private String flowableProcessInstanceId;

    private String flowableProcessDefinitionId;

    private String formDefinitionId;

    private String instanceNo;

    private String instanceTitle;

    private String businessKey;

    private String starterUserId;

    private String starterUsername;

    private String starterRealname;

    private String status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String currentTaskNames;

    private String currentAssigneeNames;
}
