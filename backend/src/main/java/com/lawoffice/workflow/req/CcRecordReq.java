package com.lawoffice.workflow.req;

import com.lawoffice.framework.req.BaseReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CcRecordReq extends BaseReq {
    private String processInstanceId;
    private String processModelId;
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
    private String messageId;
    private String remark;
}
