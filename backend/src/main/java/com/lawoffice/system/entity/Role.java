package com.lawoffice.system.entity;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.ExcelIgnore;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.entity.BaseTenantEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
@Schema(description = "角色")
public class Role extends BaseTenantEntity {

    @ExcelProperty("角色名称")
    @Schema(description = "角色名称")
    private String roleName;

    @ExcelProperty("角色编码")
    @Schema(description = "角色编码")
    private String roleCode;

    @ExcelProperty("描述")
    @Schema(description = "描述")
    private String description;
}
