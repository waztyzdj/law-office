package com.lawoffice.system.req;

import com.lawoffice.framework.req.BaseReq;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 部门角色请求对象。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DepartRoleReq extends BaseReq {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "部门ID不能为空")
    @Size(max = 64, message = "部门ID不能超过64个字符")
    private String departId;

    @NotBlank(message = "部门角色名称不能为空")
    @Size(max = 1024, message = "部门角色名称不能超过1024个字符")
    private String roleName;

    @NotBlank(message = "部门角色编码不能为空")
    @Size(max = 64, message = "部门角色编码不能超过64个字符")
    private String roleCode;

    private Integer workflowEnabled;

    @Size(max = 1024, message = "描述不能超过1024个字符")
    private String description;
}
