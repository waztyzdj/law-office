package com.lawoffice.message.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.entity.BaseTenantEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_message_receiver")
@Schema(description = "站内消息接收人")
public class SysMessageReceiver extends BaseTenantEntity {

    private String messageId;

    private String receiverId;

    private String receiverName;

    private Integer readStatus;

    private LocalDateTime readTime;

    private Integer starFlag;

    private Integer archiveFlag;

    private Integer remindStatus;

    private LocalDateTime lastRemindTime;
}
