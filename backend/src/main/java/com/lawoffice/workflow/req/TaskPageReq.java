package com.lawoffice.workflow.req;

import com.lawoffice.framework.req.BasePageReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TaskPageReq extends BasePageReq {

    private String processInstanceId;

    private String instanceTitle;

    private String instanceNo;

    private String taskName;

    private String taskType;

    private String status;

    private String starterRealname;

    private String assigneeRealname;

    private String startTimeGe;

    private String startTimeLe;

    private String completeTimeGe;

    private String completeTimeLe;
}
