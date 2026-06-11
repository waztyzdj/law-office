package com.lawoffice.system.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * 部门负责人保存请求。
 */
@Data
public class DepartLeaderReq implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "部门ID不能为空")
    private String departId;

    private String userId;
}
