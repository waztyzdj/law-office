package com.lawoffice.system.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "密码修改信息")
public class ChangePwdReq {
    @Schema(description = "旧密码（用于修改密码时）")
    private String oldPassword;

    @Size(min = 6, message = "新密码长度不能小于6位")
    @Schema(description = "新密码（用于修改密码时）")
    private String newPassword;

    @Schema(description = "确认新密码（用于修改密码时）")
    private String confirmPassword;
}