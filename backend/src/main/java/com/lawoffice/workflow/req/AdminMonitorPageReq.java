package com.lawoffice.workflow.req;

import com.lawoffice.framework.req.BasePageReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminMonitorPageReq extends BasePageReq {

    private String instanceTitle;

    private String instanceNo;

    private String categoryId;

    private String processKey;

    private String processName;

    private String starterRealname;

    private String currentTaskNames;

    private String currentAssigneeNames;

    private String status;

    private String startTimeGe;

    private String startTimeLe;

    private String updateTimeGe;

    private String updateTimeLe;
}
