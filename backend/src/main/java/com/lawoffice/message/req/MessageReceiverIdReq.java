package com.lawoffice.message.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MessageReceiverIdReq {

    @NotBlank(message = "收件消息ID不能为空")
    private String id;
}
