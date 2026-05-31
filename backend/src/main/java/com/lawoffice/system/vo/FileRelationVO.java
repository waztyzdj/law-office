package com.lawoffice.system.vo;

import lombok.Data;

@Data
public class FileRelationVO {

    private String id;

    private String fileId;

    private String bizType;

    private String bizId;

    private Integer relationType;

    private Integer sortOrder;
}
