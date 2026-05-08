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

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
@ModuleInfo(value = "user", name = "用户管理", description = "系统用户信息管理")
@Schema(description = "用户")
public class User extends BaseEntity {
    
    @ExcelProperty("登录账号")
    @Schema(description = "登录账号")
    private String username;
    
    @ExcelProperty("真实姓名")
    @Schema(description = "真实姓名")
    private String realname;
    
    @ExcelIgnore
    @TableField(select = false)  // 查询时不返回此字段
    @Schema(description = "密码")
    private String password;
    
    @ExcelIgnore
    @TableField(select = false)  // 查询时不返回此字段
    @Schema(description = "md5密码盐")
    private String salt;
    
    @ExcelIgnore
    @Schema(description = "头像")
    private String avatar;
    
    @ExcelProperty("生日")
    @Schema(description = "生日")
    private LocalDateTime birthday;
    
    @ExcelProperty("性别")
    @Schema(description = "性别(0-默认未知,1-男,2-女)")
    private Integer sex;
    
    @ExcelProperty("邮箱")
    @Schema(description = "电子邮件")
    private String email;
    
    @ExcelProperty("电话")
    @Schema(description = "电话")
    private String phone;
    
    @ExcelIgnore
    @Schema(description = "状态(1-正常,2-冻结)")
    private Integer status;
    
    @ExcelProperty("工号")
    @Schema(description = "工号，唯一键")
    private String workNo;
    
    @ExcelProperty("职务")
    @Schema(description = "职务，关联职务表")
    private String post;
    
    @ExcelProperty("座机号")
    @Schema(description = "座机号")
    private String telephone;
    
    @ExcelProperty("身份证号")
    @Schema(description = "身份证号")
    private String idCard;
    
    @ExcelIgnore
    @Schema(description = "身份（1普通成员 2上级）")
    private Integer userIdentity;
    
    @ExcelIgnore
    @Schema(description = "负责部门")
    private String departIds;
    
    @ExcelIgnore
    @Schema(description = "设备ID")
    private String clientId;
    
    @ExcelIgnore
    @Schema(description = "上次登录选择租户ID")
    private String loginTenantId;
    
    @ExcelIgnore
    @Schema(description = "流程入职离职状态")
    private String bpmStatus;
    
    @ExcelProperty("状态")
    @TableField(exist = false)
    private String statusText;
    
    @ExcelProperty("性别")
    @TableField(exist = false)
    private String sexText;
    
    /**
     * 获取状态文本（用于导出）
     */
    public String getStatusText() {
        if (status == null) {
            return "";
        }
        return status == 1 ? "正常" : "冻结";
    }
    
    /**
     * 获取性别文本（用于导出）
     */
    public String getSexText() {
        if (sex == null) {
            return "未知";
        }
        switch (sex) {
            case 1:
                return "男";
            case 2:
                return "女";
            default:
                return "未知";
        }
    }
}
