package com.lawoffice.system.entity;

import com.alibaba.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user_depart")
@Schema(description = "用户部门")
public class UserDepart extends BaseEntity {

    @ExcelProperty("用户ID")
    @Schema(description = "用户id")
    private String userId;

    @ExcelProperty("部门ID")
    @Schema(description = "部门id")
    private String depId;
}
