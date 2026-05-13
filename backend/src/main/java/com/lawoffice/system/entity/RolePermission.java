package com.lawoffice.system.entity;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.ExcelIgnore;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.entity.BaseTenantEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role_permission")
@Schema(description = "角色权限")
public class RolePermission extends BaseTenantEntity {

    @ExcelProperty("角色ID")
    @Schema(description = "角色id")
    private String roleId;

    @ExcelProperty("权限ID")
    @Schema(description = "权限id")
    private String permissionId;

    @ExcelProperty("数据权限IDs")
    @Schema(description = "数据权限ids")
    private String dataRuleIds;

    @ExcelProperty("操作时间")
    @Schema(description = "操作时间")
    private LocalDateTime operateDate;

    @ExcelProperty("操作IP")
    @Schema(description = "操作ip")
    private String operateIp;
}
