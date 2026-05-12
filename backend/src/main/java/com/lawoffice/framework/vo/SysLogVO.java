package com.lawoffice.framework.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "系统日志")
public class SysLogVO extends BaseVO {

    @Schema(description = "日志类型")
    private Integer logType;

    @Schema(description = "日志内容")
    private String logContent;

    @Schema(description = "操作类型")
    private Integer operateType;

    @Schema(description = "用户ID")
    private String userid;

    @Schema(description = "用户名")
    private String username;

    @Schema(description = "IP地址")
    private String ip;

    @Schema(description = "方法")
    private String method;

    @Schema(description = "请求URL")
    private String requestUrl;

    @Schema(description = "请求参数")
    private String requestParam;

    @Schema(description = "请求类型")
    private String requestType;

    @Schema(description = "耗时")
    private Long costTime;

    @Schema(description = "客户端类型")
    private String clientType;
}
