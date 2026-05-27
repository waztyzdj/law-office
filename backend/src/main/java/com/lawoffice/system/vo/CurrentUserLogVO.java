package com.lawoffice.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "当前用户日志")
public class CurrentUserLogVO {

    @Schema(description = "日志ID")
    private String id;

    @Schema(description = "日志类型（1登录日志，2操作日志，3租户操作日志）")
    private Integer logType;

    @Schema(description = "日志内容")
    private String logContent;

    @Schema(description = "操作类型")
    private Integer operateType;

    @Schema(description = "IP")
    private String ip;

    @Schema(description = "请求类型")
    private String requestType;

    @Schema(description = "请求路径")
    private String requestUrl;

    @Schema(description = "客户端类型")
    private String clientType;

    @Schema(description = "耗时(ms)")
    private Long costTime;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
