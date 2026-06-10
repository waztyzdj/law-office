package com.lawoffice.workflow.req;

import com.lawoffice.framework.req.BasePageReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class StartedInstancePageReq extends BasePageReq {

    private String status;

    private String instanceTitle;

    private String instanceNo;

    private String processName;

    private String currentTaskNames;

    private String currentAssigneeNames;

    private String startTimeGe;

    private String startTimeLe;

    private String endTimeGe;

    private String endTimeLe;
}
