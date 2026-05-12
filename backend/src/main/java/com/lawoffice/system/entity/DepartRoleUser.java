package com.lawoffice.system.entity;

import com.alibaba.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_depart_role_user")
@Schema(description = "部门角色用户")
public class DepartRoleUser extends BaseEntity {

    @ExcelProperty("用户ID")
    @Schema(description = "用户id")
    private String userId;

    @ExcelProperty("部门角色ID")
    @Schema(description = "部门角色id")
    private String droleId;
}
