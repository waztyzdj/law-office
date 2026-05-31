package com.lawoffice.message.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MessageIdReq {

    @NotBlank(message = "消息ID不能为空")
    private String id;
}
