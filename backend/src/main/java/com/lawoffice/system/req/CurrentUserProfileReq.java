package com.lawoffice.system.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "当前用户个人资料修改请求")
public class CurrentUserProfileReq {

    @Size(max = 64, message = "真实姓名不能超过64个字符")
    @Schema(description = "真实姓名")
    private String realname;

    @Size(max = 1024, message = "头像地址不能超过1024个字符")
    @Schema(description = "头像地址")
    private String avatar;

    @Email(message = "邮箱格式不正确")
    @Size(max = 45, message = "邮箱不能超过45个字符")
    @Schema(description = "电子邮件")
    private String email;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号")
    private String phone;

    @Size(max = 45, message = "座机号不能超过45个字符")
    @Schema(description = "座机号")
    private String telephone;

    @Size(max = 64, message = "职务不能超过64个字符")
    @Schema(description = "职务")
    private String post;
}
