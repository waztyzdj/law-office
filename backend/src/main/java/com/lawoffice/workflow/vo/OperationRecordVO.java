package com.lawoffice.workflow.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lawoffice.framework.vo.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class OperationRecordVO extends BaseVO {
    private String tenantId;
    private String processInstanceId;
    private String taskId;
    private String flowableTaskId;
    private String nodeId;
    private String nodeName;
    private String action;
    private String operatorUserId;
    private String operatorUsername;
    private String operatorRealname;
    private String targetUserId;
    private String targetUsername;
    private String targetRealname;
    private String targetNodeId;
    private String targetNodeName;
    private String comment;
    private String formDataSnapshotJson;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime operateTime;
}
