package com.lawoffice.system.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DocumentShareSourceVO {

    private String sourceType;

    private String fileId;

    private String fileName;

    private String sharedBy;

    private String targetType;

    private String targetId;

    private String targetName;

    private String permission;

    private String inheritedFromFileId;

    private String inheritedFromFileName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime expireTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
}
