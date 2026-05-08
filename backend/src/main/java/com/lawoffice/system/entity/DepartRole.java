package com.lawoffice.system.entity;

import com.alibaba.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.entity.BaseEntity;
import com.lawoffice.framework.annotation.ModuleInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_depart_role")
@ModuleInfo(value = "depart-role", name = "部门角色管理", description = "系统部门角色信息管理")
@Schema(description = "部门角色")
public class DepartRole extends BaseEntity {

    @ExcelProperty("部门ID")
    @Schema(description = "部门id")
    private String departId;

    @ExcelProperty("部门角色名称")
    @Schema(description = "部门角色名称")
    private String roleName;

    @ExcelProperty("部门角色编码")
    @Schema(description = "部门角色编码")
    private String roleCode;

    @ExcelProperty("描述")
    @Schema(description = "描述")
    private String description;
}
