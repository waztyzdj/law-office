package com.lawoffice.workflow.vo;

import lombok.Data;

@Data
public class RuntimeFieldPermissionVO {

    private String fieldKey;

    private String permission;

    private Integer requiredFlag;
}
