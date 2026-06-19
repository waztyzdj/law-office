package com.lawoffice.workflow.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lawoffice.framework.vo.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class TaskVO extends BaseVO {
    private String tenantId;
    private String processInstanceId;
    private String parentTaskId;
    private String flowableTaskId;
    private String nodeId;
    private String taskName;
    private String taskType;
    private String approvalMode;
    private String taskGroupId;
    private Integer groupTotal;
    private Integer groupCompleted;
    private String ownerUserId;
    private String ownerUsername;
    private String ownerRealname;
    private String assigneeUserId;
    private String assigneeUsername;
    private String assigneeRealname;
    private String status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime dueTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastRemindTime;
    private Integer remindCount;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime claimTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime completeTime;
}
