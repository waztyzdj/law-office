package com.lawoffice.workflow.req;

import com.lawoffice.framework.req.BaseReq;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminMonitorActionReq extends BaseReq {

    private String processInstanceId;

    private String taskId;

    private String targetUserId;

    @Size(max = 500, message = "维护原因不能超过500个字符")
    private String operationReason;
}
