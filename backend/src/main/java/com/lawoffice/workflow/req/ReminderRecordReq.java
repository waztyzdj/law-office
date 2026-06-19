package com.lawoffice.workflow.req;

import com.lawoffice.framework.req.BaseReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ReminderRecordReq extends BaseReq {
    private String processInstanceId;
    private String taskId;
    private String flowableTaskId;
    private String remindType;
    private String senderUserId;
    private String senderUsername;
    private String senderRealname;
    private String receiverUserId;
    private String receiverUsername;
    private String receiverRealname;
    private String messageId;
    private Integer remindRound;
    private String remark;
}
