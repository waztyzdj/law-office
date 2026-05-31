package com.lawoffice.message.req;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MessageActionReq {

    @Min(value = 1, message = "动作类型不正确")
    @Max(value = 99, message = "动作类型不正确")
    private Integer actionType;

    @Size(max = 64, message = "动作名称不能超过64个字符")
    private String actionName;

    @Size(max = 512, message = "内部路由路径不能超过512个字符")
    private String routePath;

    private String routeQuery;

    @Size(max = 1024, message = "外部链接不能超过1024个字符")
    private String externalUrl;

    @Size(max = 64, message = "业务类型不能超过64个字符")
    private String bizType;

    @Size(max = 64, message = "业务ID不能超过64个字符")
    private String bizId;

    private Integer openType;

    private Integer sortOrder;
}
