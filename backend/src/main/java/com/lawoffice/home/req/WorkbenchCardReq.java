package com.lawoffice.home.req;

import com.lawoffice.framework.req.BaseReq;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WorkbenchCardReq extends BaseReq {
    @Size(max = 64, message = "卡片编码不能超过64个字符")
    private String cardCode;
    @Size(max = 100, message = "卡片名称不能超过100个字符")
    private String cardName;
    @Size(max = 100, message = "组件标识不能超过100个字符")
    private String componentKey;
    @Size(max = 128, message = "权限码不能超过128个字符")
    private String permissionCode;
    private String status;
    private Integer defaultVisible;
    private Integer defaultSort;
    private String defaultSize;
    private Integer defaultRefreshInterval;
    private String configJson;
    private JsonNode config;
    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;
}
