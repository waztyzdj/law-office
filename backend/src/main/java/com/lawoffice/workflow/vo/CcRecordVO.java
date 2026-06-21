package com.lawoffice.workflow.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lawoffice.framework.vo.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class CcRecordVO extends BaseVO {
    private String tenantId;
    private String processInstanceId;
    private String processModelId;
    private String processName;
    private String instanceNo;
    private String instanceTitle;
    private String processStatus;
    private String starterUserId;
    private String starterUsername;
    private String starterRealname;
    private String taskId;
    private String nodeId;
    private String nodeName;
    private String triggerAction;
    private String sourceType;
    private String sourceId;
    private String receiverUserId;
    private String receiverUsername;
    private String receiverRealname;
    private String status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime readTime;
    private String messageId;
    private String remark;
}
