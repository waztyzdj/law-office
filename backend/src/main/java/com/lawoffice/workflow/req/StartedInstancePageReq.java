package com.lawoffice.workflow.req;

import com.lawoffice.framework.req.BasePageReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class StartedInstancePageReq extends BasePageReq {

    private String status;

    private String instanceTitle;
}
