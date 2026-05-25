package com.lawoffice.system.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 切换租户请求对象。
 */
@Data
public class SwitchTenantReq implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "租户ID不能为空")
    private String tenantId;
}
