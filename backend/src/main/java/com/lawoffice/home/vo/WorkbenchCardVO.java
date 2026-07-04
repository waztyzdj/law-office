package com.lawoffice.home.vo;

import com.fasterxml.jackson.databind.JsonNode;
import com.lawoffice.framework.vo.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WorkbenchCardVO extends BaseVO {
    private String tenantId;
    private String cardCode;
    private String cardName;
    private String componentKey;
    private String permissionCode;
    private String status;
    private Integer defaultVisible;
    private Integer defaultSort;
    private String defaultSize;
    private Integer defaultRefreshInterval;
    private String configJson;
    private JsonNode config;
    private String remark;
}
