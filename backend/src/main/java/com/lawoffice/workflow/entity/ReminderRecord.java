package com.lawoffice.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.framework.entity.BaseTenantEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_reminder_record")
@ModuleInfo(value = "workflow:reminder", name = "审批催办", description = "审批中心催办与超时提醒记录")
@Schema(description = "审批中心催办与超时提醒记录")
public class ReminderRecord extends BaseTenantEntity {

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

    private LocalDateTime operateTime;

    private String remark;
}
