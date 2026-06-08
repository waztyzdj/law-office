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
@TableName("wf_task")
@ModuleInfo(value = "workflow:task", name = "审批任务", description = "审批中心任务扩展")
@Schema(description = "审批中心任务扩展")
public class Task extends BaseTenantEntity {

    private String processInstanceId;

    private String parentTaskId;

    private String flowableTaskId;

    private String nodeId;

    private String taskName;

    private String taskType;

    private String ownerUserId;

    private String ownerUsername;

    private String ownerRealname;

    private String assigneeUserId;

    private String assigneeUsername;

    private String assigneeRealname;

    private String status;

    private LocalDateTime claimTime;

    private LocalDateTime completeTime;
}
