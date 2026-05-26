package com.lawoffice.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 部门权限来源视图对象。
 */
@Data
@Schema(description = "部门权限来源")
public class DepartPermissionSourceVO {

    @Schema(description = "权限ID")
    private String permissionId;

    @Schema(description = "权限名称")
    private String permissionName;

    @Schema(description = "权限编码")
    private String perms;

    @Schema(description = "来源类型：depart-部门直接权限 role-部门角色权限")
    private String sourceType;

    @Schema(description = "来源ID")
    private String sourceId;

    @Schema(description = "来源名称")
    private String sourceName;
}
