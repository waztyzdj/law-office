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
@TableName("sys_user_tenant")
@ModuleInfo(value = "user-tenant", name = "用户租户管理", description = "系统用户租户关联信息管理")
@Schema(description = "用户租户")
public class UserTenant extends BaseEntity {

    @ExcelProperty("用户ID")
    @Schema(description = "用户id")
    private String userId;

    @ExcelProperty("租户ID")
    @Schema(description = "租户id")
    private String tenantId;

    @ExcelProperty("状态")
    @Schema(description = "状态(1 正常 2 离职 3 待审核 4 审核未通过)")
    private String status;
}
