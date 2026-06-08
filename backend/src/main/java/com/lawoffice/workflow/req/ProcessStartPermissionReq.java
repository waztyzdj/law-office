package com.lawoffice.workflow.req;

import com.lawoffice.framework.req.BaseReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProcessStartPermissionReq extends BaseReq {
    private String processModelId;
    private String targetType;
    private String targetId;
    private String status;
}
