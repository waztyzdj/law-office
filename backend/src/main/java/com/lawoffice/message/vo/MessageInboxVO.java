package com.lawoffice.message.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageInboxVO {

    private String id;

    private String messageId;

    private String title;

    private Integer messageType;

    private String bizType;

    private Integer priority;

    private String senderId;

    private String senderName;

    private String senderAvatar;

    private Integer sendStatus;

    private Integer readStatus;

    private Integer starFlag;

    private Integer archiveFlag;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime sendTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime readTime;
}
