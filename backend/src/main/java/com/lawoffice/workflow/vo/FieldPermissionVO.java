package com.lawoffice.workflow.vo;

import com.lawoffice.framework.vo.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FieldPermissionVO extends BaseVO {
    private String tenantId;
    private String processModelId;
    private String nodeId;
    private String fieldKey;
    private String permission;
    private Integer requiredFlag;
}
