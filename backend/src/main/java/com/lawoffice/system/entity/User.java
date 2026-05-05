package com.lawoffice.system.entity;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.ExcelIgnore;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.entity.BaseEntity;
import com.lawoffice.framework.annotation.ModuleInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
@ModuleInfo(name = "用户管理", description = "系统用户信息管理")
@Schema(description = "用户")
public class User extends BaseEntity {
    
    @ExcelProperty("用户名")
    @Schema(description = "用户名")
    private String username;
    
    @ExcelIgnore
    @Schema(description = "密码")
    private String password;
    
    @ExcelProperty("真实姓名")
    @Schema(description = "真实姓名")
    private String realName;
    
    @ExcelProperty("身份证号")
    @Schema(description = "身份证号")
    private String idCard;
    
    @ExcelProperty("手机号")
    @Schema(description = "手机号")
    private String phone;
    
    @ExcelProperty("邮箱")
    @Schema(description = "邮箱")
    private String email;
    
    @ExcelIgnore
    @Schema(description = "状态：0-禁用，1-启用")
    private Integer status;
    
    @ExcelProperty("状态")
    @TableField(exist = false)
    private String statusText;
    
    /**
     * 获取状态文本（用于导出）
     */
    public String getStatusText() {
        if (status == null) {
            return "";
        }
        return status == 1 ? "启用" : "停用";
    }
}
