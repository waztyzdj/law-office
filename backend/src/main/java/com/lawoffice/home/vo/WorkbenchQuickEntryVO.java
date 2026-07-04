package com.lawoffice.home.vo;

import com.fasterxml.jackson.databind.JsonNode;
import com.lawoffice.framework.vo.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WorkbenchQuickEntryVO extends BaseVO {
    private String tenantId;
    private String ownerType;
    private String ownerUserId;
    private String entryCode;
    private String entryName;
    private String entryType;
    private String menuId;
    private String path;
    private String permissionCode;
    private String icon;
    private Integer sortNo;
    private String status;
    private String configJson;
    private JsonNode config;
}
