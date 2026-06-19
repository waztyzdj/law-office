package com.lawoffice.workflow.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lawoffice.framework.vo.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class ReminderRecordVO extends BaseVO {
    private String tenantId;
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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime operateTime;
    private String remark;
}
