package com.lawoffice.system.entity;

import com.alibaba.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.entity.BaseTenantEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user_depart")
@Schema(description = "用户部门")
public class UserDepart extends BaseTenantEntity {

    @ExcelProperty("用户ID")
    @Schema(description = "用户id")
    private String userId;

    @ExcelProperty("部门ID")
    @Schema(description = "部门id")
    private String depId;

    @ExcelProperty("是否主部门")
    @Schema(description = "是否主部门：0-否，1-是")
    private Integer primaryDepartFlag;

    @ExcelProperty("是否部门负责人")
    @Schema(description = "是否部门负责人：0-否，1-是")
    private Integer departLeaderFlag;

    @ExcelProperty("直属上级用户ID")
    @Schema(description = "直属上级用户ID，按当前部门维度维护")
    private String supervisorUserId;
}
