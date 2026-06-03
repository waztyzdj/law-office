package com.lawoffice.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.entity.BaseTenantEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_file_version")
@Schema(description = "Document history version")
public class SysFileVersion extends BaseTenantEntity {

    @Schema(description = "File id")
    private String fileId;

    @Schema(description = "Version number in one file")
    private Integer versionNo;

    @Schema(description = "Version type: upload/final/restore")
    private String versionType;

    @Schema(description = "Immutable MinIO object name")
    private String objectName;

    @Schema(description = "ONLYOFFICE changes object name")
    private String changesObjectName;

    @Schema(description = "Version file name")
    private String fileName;

    @Schema(description = "File type")
    private String fileType;

    @Schema(description = "MIME content type")
    private String contentType;

    @Schema(description = "File size in bytes")
    private Long fileSize;

    @Schema(description = "SHA-256 checksum")
    private String checksum;

    @Schema(description = "ONLYOFFICE document key")
    private String documentKey;

    @Schema(description = "ONLYOFFICE server version")
    private String serverVersion;

    @Schema(description = "ONLYOFFICE history payload")
    private String historyJson;

    @Schema(description = "Editor user id")
    private String editorId;

    @Schema(description = "Editor display name")
    private String editorName;

    @Schema(description = "Version remark")
    private String remark;
}
