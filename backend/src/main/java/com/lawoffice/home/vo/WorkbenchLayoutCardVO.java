package com.lawoffice.home.vo;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class WorkbenchLayoutCardVO {
    private String cardCode;
    private String cardName;
    private String componentKey;
    private String permissionCode;
    private Boolean visible;
    private Integer sortNo;
    private String size;
    private Integer gridX;
    private Integer gridY;
    private Integer gridW;
    private Integer gridH;
    private Integer refreshInterval;
    private String configJson;
    private JsonNode config;
    private Boolean systemVisible;
    private Boolean userCustomized;
}
