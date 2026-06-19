package com.lawoffice.workflow.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lawoffice.framework.vo.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class BranchRecordVO extends BaseVO {
    private String tenantId;
    private String processInstanceId;
    private String processModelId;
    private String sourceNodeId;
    private String sourceNodeName;
    private String branchId;
    private String branchName;
    private String targetNodeId;
    private String targetNodeName;
    private String conditionSnapshotJson;
    private String formDataSnapshotJson;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime matchedTime;
}
