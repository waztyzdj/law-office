package com.lawoffice.system.vo;

import lombok.Data;

@Data
public class FileUploadVO {

    private String fileId;

    private String fileName;

    private String fileUrl;

    private String objectName;

    private String fileType;

    private Long fileSize;

    private String bizType;

    private String bizId;

    private String relationId;
}
