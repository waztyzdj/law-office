package com.lawoffice.message.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.entity.BaseTenantEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_message")
@Schema(description = "站内消息")
public class SysMessage extends BaseTenantEntity {

    private String sendRecordId;

    private String title;

    private String content;

    private Integer contentType;

    private Integer messageType;

    private Integer priority;

    private String senderId;

    private String senderName;

    private Integer sendStatus;

    private LocalDateTime sendTime;

    private LocalDateTime expireTime;

    private Integer senderDeleteFlag;
}
