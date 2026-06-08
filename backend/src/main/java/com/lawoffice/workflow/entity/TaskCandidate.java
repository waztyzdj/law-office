package com.lawoffice.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.framework.entity.BaseTenantEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_task_candidate")
@ModuleInfo(value = "workflow:task-candidate", name = "审批任务候选人", description = "审批中心任务候选人")
@Schema(description = "审批中心任务候选人")
public class TaskCandidate extends BaseTenantEntity {

    private String taskId;

    private String flowableTaskId;

    private String candidateUserId;

    private String candidateUsername;

    private String candidateRealname;

    private String sourceType;

    private String sourceId;

    private String status;
}
