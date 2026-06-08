package com.lawoffice.workflow.req;

import com.lawoffice.framework.req.BaseReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FieldPermissionReq extends BaseReq {
    private String processModelId;
    private String nodeId;
    private String fieldKey;
    private String permission;
    private Integer requiredFlag;
}
