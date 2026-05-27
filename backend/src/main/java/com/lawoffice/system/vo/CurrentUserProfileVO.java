package com.lawoffice.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "当前用户个人资料")
public class CurrentUserProfileVO {

    @Schema(description = "用户ID")
    private String id;

    @Schema(description = "登录账号")
    private String username;

    @Schema(description = "真实姓名")
    private String realname;

    @Schema(description = "头像地址")
    private String avatar;

    @Schema(description = "电子邮件")
    private String email;

    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "座机号")
    private String telephone;

    @Schema(description = "工号")
    private String workNo;

    @Schema(description = "职务")
    private String post;

    @Schema(description = "状态(1-正常,8-冻结)")
    private Integer status;

    @Schema(description = "当前租户ID")
    private String tenantId;

    @Schema(description = "当前租户名称")
    private String tenantName;
}
