package com.lawoffice.message.req;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MessageReceiverIdsReq {

    @NotEmpty(message = "收件消息ID不能为空")
    private List<String> ids = new ArrayList<>();
}
