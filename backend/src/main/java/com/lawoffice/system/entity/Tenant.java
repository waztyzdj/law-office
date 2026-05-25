package com.lawoffice.system.entity;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.ExcelIgnore;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.entity.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_tenant")
@Schema(description = "多租户")
public class Tenant extends BaseEntity {

    @ExcelProperty("租户名称")
    @Schema(description = "租户名称")
    private String name;

    @ExcelProperty("创建时间")
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    @ExcelProperty("创建人")
    @Schema(description = "创建人")
    private String createBy;

    @ExcelProperty("开始时间")
    @Schema(description = "开始时间")
    private LocalDateTime beginDate;

    @ExcelProperty("结束时间")
    @Schema(description = "结束时间")
    private LocalDateTime endDate;

    @ExcelProperty("状态")
    @Schema(description = "状态 1正常 0冻结")
    private Integer status;

    @ExcelProperty("所属行业")
    @Schema(description = "所属行业")
    private String trade;

    @ExcelProperty("公司规模")
    @Schema(description = "公司规模")
    private String companySize;

    @ExcelProperty("公司地址")
    @Schema(description = "公司地址")
    private String companyAddress;

    @ExcelProperty("公司Logo")
    @Schema(description = "公司logo")
    private String companyLogo;

    @ExcelProperty("门牌号")
    @Schema(description = "门牌号")
    private String houseNumber;

    @ExcelProperty("工作地点")
    @Schema(description = "工作地点")
    private String workPlace;

    @ExcelProperty("二级域名")
    @Schema(description = "二级域名")
    private String secondaryDomain;

    @ExcelProperty("登录背景图片")
    @Schema(description = "登录背景图片")
    private String loginBkgdImg;

    @ExcelProperty("职级")
    @Schema(description = "职级")
    private String position;

    @ExcelProperty("部门")
    @Schema(description = "部门")
    private String department;

    @ExcelProperty("状态")
    @Schema(description = "允许申请管理员 1允许 0不允许")
    private Integer applyStatus;

    @ExcelIgnore
    @TableField(exist = false)
    @Schema(description = "租户管理员用户ID列表")
    private List<String> adminUserIds = new ArrayList<>();

    @ExcelIgnore
    @TableField(exist = false)
    @Schema(description = "原始租户编码")
    private String originalId;
}
