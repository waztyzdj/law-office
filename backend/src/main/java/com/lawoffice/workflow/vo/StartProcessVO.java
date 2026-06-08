package com.lawoffice.workflow.vo;

import lombok.Data;

@Data
public class StartProcessVO {

    private String processInstanceId;

    private String formInstanceId;

    private String flowableProcessInstanceId;

    private String instanceNo;

    private String status;
}
