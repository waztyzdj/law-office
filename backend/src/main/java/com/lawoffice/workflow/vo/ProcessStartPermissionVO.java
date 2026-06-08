package com.lawoffice.workflow.vo;

import com.lawoffice.framework.vo.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProcessStartPermissionVO extends BaseVO {
    private String tenantId;
    private String processModelId;
    private String targetType;
    private String targetId;
    private String status;
}
