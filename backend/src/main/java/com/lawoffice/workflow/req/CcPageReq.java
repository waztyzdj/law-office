package com.lawoffice.workflow.req;

import com.lawoffice.framework.req.BasePageReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class CcPageReq extends BasePageReq {

    private String processInstanceId;

    private String instanceTitle;

    private String status;

    private String createTimeGe;

    private String createTimeLe;
}
