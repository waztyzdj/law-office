package com.lawoffice.system.entity;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.ExcelIgnore;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.entity.BaseEntity;
import com.lawoffice.framework.annotation.ModuleInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user_role")
@ModuleInfo(name = "用户角色管理", description = "系统用户角色关联信息管理")
@Schema(description = "用户角色")
public class UserRole extends BaseEntity {

    @ExcelProperty("用户ID")
    @Schema(description = "用户id")
    private String userId;

    @ExcelProperty("角色ID")
    @Schema(description = "角色id")
    private String roleId;

    @ExcelIgnore
    @Schema(description = "租户ID")
    private String tenantId;
}
