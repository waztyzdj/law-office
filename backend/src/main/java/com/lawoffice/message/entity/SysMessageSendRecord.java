package com.lawoffice.message.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.entity.BaseTenantEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_message_send_record")
@Schema(description = "站内消息发送记录")
public class SysMessageSendRecord extends BaseTenantEntity {

    private String messageId;

    private String sendBatchNo;

    private Integer sendScene;

    private Integer sendScope;

    private String receiverSnapshot;

    private String channelTypes;

    private String senderId;

    private String senderName;

    private Integer receiverCount;

    private Integer successCount;

    private Integer failCount;

    private Integer readCount;

    private Integer sendStatus;

    private LocalDateTime sendTime;

    private LocalDateTime finishTime;

    private String failReason;

    private String remark;
}
