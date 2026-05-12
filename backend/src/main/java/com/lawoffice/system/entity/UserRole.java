package com.lawoffice.system.entity;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.ExcelIgnore;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user_role")
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
