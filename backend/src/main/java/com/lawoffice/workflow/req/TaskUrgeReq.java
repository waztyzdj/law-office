package com.lawoffice.workflow.req;

import com.lawoffice.framework.req.BaseReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TaskUrgeReq extends BaseReq {

    private String processInstanceId;

    private String taskId;

    private String remark;
}
