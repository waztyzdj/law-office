package com.lawoffice.message.vo;

import lombok.Data;

@Data
public class MessageAttachmentVO {

    private String id;

    private String fileId;

    private String fileName;

    private String fileType;

    private Double fileSize;

    private Integer sortOrder;
}
