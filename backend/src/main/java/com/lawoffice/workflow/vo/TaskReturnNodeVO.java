package com.lawoffice.workflow.vo;

import lombok.Data;

@Data
public class TaskReturnNodeVO {

    private String nodeId;

    private String nodeName;

    private String nodeType;

    private Integer sortOrder;
}
