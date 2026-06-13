package com.lawoffice.workflow.vo;

import lombok.Data;

import java.util.List;

@Data
public class AssigneeSelectNodeVO {

    private String nodeId;

    private String nodeName;

    private String assigneeType;

    private String selectType;

    private Boolean required;

    private List<AssigneeOptionVO> options;
}
