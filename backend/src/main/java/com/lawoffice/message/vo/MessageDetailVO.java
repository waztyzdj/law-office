package com.lawoffice.message.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class MessageDetailVO {

    private String id;

    private String receiverMessageId;

    private String title;

    private String content;

    private Integer contentType;

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
    private LocalDateTime expireTime;

    private List<String> receiverNames = new ArrayList<>();

    private List<MessageActionVO> actions = new ArrayList<>();

    private List<MessageAttachmentVO> attachments = new ArrayList<>();
}
