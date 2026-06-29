package com.lawoffice.workflow.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lawoffice.framework.vo.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminOperationRecordVO extends BaseVO {

    private String tenantId;

    private String processInstanceId;

    private String taskId;

    private String operationType;

    private String operationReason;

    private String beforeSnapshotJson;

    private String afterSnapshotJson;

    private String operatorUserId;

    private String operatorUsername;

    private String operatorRealname;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime operateTime;

    private String status;

    private String errorMessage;
}
