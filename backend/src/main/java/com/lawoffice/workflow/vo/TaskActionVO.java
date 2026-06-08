package com.lawoffice.workflow.vo;

import lombok.Data;

@Data
public class TaskActionVO {

    private String taskId;

    private String processInstanceId;

    private String taskStatus;

    private String processStatus;
}
