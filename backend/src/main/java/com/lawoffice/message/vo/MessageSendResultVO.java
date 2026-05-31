package com.lawoffice.message.vo;

import lombok.Data;

@Data
public class MessageSendResultVO {

    private String messageId;

    private String sendRecordId;

    private String sendBatchNo;

    private Integer receiverCount;
}
