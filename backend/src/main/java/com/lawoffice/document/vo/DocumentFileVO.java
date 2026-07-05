package com.lawoffice.document.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lawoffice.framework.vo.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class DocumentFileVO extends BaseVO {

    private String fileName;

    private String fileType;

    private String storeType;

    private String parentId;

    private Long fileSize;

    private String izFolder;

    private String izRootFolder;

    private String izStar;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime starTime;

    private Integer downCount;

    private Integer readCount;

    private String enableDown;

    private String enableUpdat;

    private String owner;

    private Boolean ownerFlag;

    private Boolean sharedFlag;

    private Boolean hasChild;

    private Boolean canManage;

    private Boolean canDownload;

    private Boolean canUpdate;

    private Integer deleteFlag;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime deleteTime;
}
