package com.lawoffice.home.vo;

import com.lawoffice.framework.vo.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WorkbenchUserCardVO extends BaseVO {
    private String tenantId;
    private String userId;
    private String cardCode;
    private Integer visible;
    private Integer sortNo;
    private String size;
    private String configJson;
}
