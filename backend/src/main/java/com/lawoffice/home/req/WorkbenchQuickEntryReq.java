package com.lawoffice.home.req;

import com.lawoffice.framework.req.BaseReq;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WorkbenchQuickEntryReq extends BaseReq {
    @Size(max = 64, message = "入口编码不能超过64个字符")
    private String entryCode;
    @Size(max = 100, message = "入口名称不能超过100个字符")
    private String entryName;
    private String entryType;
    private String menuId;
    @Size(max = 512, message = "路径不能超过512个字符")
    private String path;
    @Size(max = 128, message = "权限码不能超过128个字符")
    private String permissionCode;
    @Size(max = 128, message = "图标不能超过128个字符")
    private String icon;
    private Integer sortNo;
    private String status;
    private String configJson;
    private JsonNode config;
}
