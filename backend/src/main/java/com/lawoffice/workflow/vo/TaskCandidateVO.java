package com.lawoffice.workflow.vo;

import com.lawoffice.framework.vo.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TaskCandidateVO extends BaseVO {
    private String tenantId;
    private String taskId;
    private String flowableTaskId;
    private String candidateUserId;
    private String candidateUsername;
    private String candidateRealname;
    private String sourceType;
    private String sourceId;
    private String status;
}
