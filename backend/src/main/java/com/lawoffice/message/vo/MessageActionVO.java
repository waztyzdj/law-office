package com.lawoffice.message.vo;

import lombok.Data;

@Data
public class MessageActionVO {

    private String id;

    private Integer actionType;

    private String actionName;

    private String routePath;

    private String routeQuery;

    private String externalUrl;

    private String bizType;

    private String bizId;

    private Integer openType;

    private Integer sortOrder;
}
