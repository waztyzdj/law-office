package com.lawoffice.message.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class SendMessageReq {

    @NotBlank(message = "消息标题不能为空")
    @Size(max = 200, message = "消息标题不能超过200个字符")
    private String title;

    private String content;

    private Integer contentType;

    private Integer messageType;

    private Integer priority;

    private Integer sendScene;

    private Integer sendScope;

    private LocalDateTime expireTime;

    @NotEmpty(message = "接收人不能为空")
    private List<String> receiverIds = new ArrayList<>();

    @Valid
    private List<MessageActionReq> actions = new ArrayList<>();

    @Valid
    private List<MessageAttachmentReq> attachments = new ArrayList<>();
}
