package com.lawoffice.message.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageSentVO {

    private String id;

    private String title;

    private Integer messageType;

    private String bizType;

    private Integer priority;

    private Integer sendStatus;

    private Integer receiverCount;

    private Integer readCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime sendTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime expireTime;
}
