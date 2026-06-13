package com.lawoffice.workflow.vo;

import lombok.Data;

@Data
public class AssigneeOptionVO {

    private String userId;

    private String username;

    private String realname;

    private String displayName;

    private String sourceType;

    private String sourceId;
}
