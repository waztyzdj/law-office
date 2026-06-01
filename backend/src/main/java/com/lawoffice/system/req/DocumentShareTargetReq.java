package com.lawoffice.system.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DocumentShareTargetReq {

    @NotBlank(message = "授权目标类型不能为空")
    @Pattern(regexp = "user|depart|role|tenant", message = "授权目标类型不正确")
    private String targetType;

    @NotBlank(message = "授权目标ID不能为空")
    @Size(max = 64, message = "授权目标ID不能超过64个字符")
    private String targetId;

    @NotBlank(message = "授权权限不能为空")
    @Pattern(regexp = "read|download|update|manage", message = "授权权限不正确")
    private String permission;
}
