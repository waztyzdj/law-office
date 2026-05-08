package com.lawoffice.system.entity;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.ExcelIgnore;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.entity.BaseEntity;
import com.lawoffice.framework.annotation.ModuleInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role_permission")
@ModuleInfo(value = "role-permission", name = "角色权限管理", description = "系统角色权限关联信息管理")
@Schema(description = "角色权限")
public class RolePermission extends BaseEntity {

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
