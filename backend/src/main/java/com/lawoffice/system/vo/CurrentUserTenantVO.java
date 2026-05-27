package com.lawoffice.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "当前用户租户信息")
public class CurrentUserTenantVO {

    @Schema(description = "租户ID")
    private String id;

    @Schema(description = "租户名称")
    private String name;

    @Schema(description = "状态 1正常 0冻结")
    private Integer status;

    @Schema(description = "开始时间")
    private LocalDateTime beginDate;

    @Schema(description = "结束时间")
    private LocalDateTime endDate;

    @Schema(description = "是否当前租户")
    private Boolean current;
}
