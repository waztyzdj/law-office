package com.lawoffice.system.entity;

import com.alibaba.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.entity.BaseTenantEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_depart_role_permission")
@Schema(description = "部门角色权限")
public class DepartRolePermission extends BaseTenantEntity {

    @ExcelProperty("部门ID")
    @Schema(description = "部门id")
    private String departId;

    @ExcelProperty("角色ID")
    @Schema(description = "角色id")
    private String roleId;

    @ExcelProperty("权限ID")
    @Schema(description = "权限id")
    private String permissionId;

    @ExcelProperty("数据权限IDs")
    @Schema(description = "数据权限ids")
    private String dataRuleIds;
}
