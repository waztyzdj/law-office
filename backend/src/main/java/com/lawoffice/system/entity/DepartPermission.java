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
@TableName("sys_depart_permission")
@ModuleInfo(name = "部门权限管理", description = "系统部门权限关联信息管理")
@Schema(description = "部门权限")
public class DepartPermission extends BaseEntity {

    @ExcelProperty("部门ID")
    @Schema(description = "部门id")
    private String departId;

    @ExcelProperty("权限ID")
    @Schema(description = "权限id")
    private String permissionId;

    @ExcelProperty("数据规则IDs")
    @Schema(description = "数据规则id")
    private String dataRuleIds;
}
