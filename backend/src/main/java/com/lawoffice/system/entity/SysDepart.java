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
@TableName("sys_depart")
@Schema(description = "组织机构")
public class SysDepart extends BaseEntity {

    @ExcelProperty("父机构ID")
    @Schema(description = "父机构ID")
    private String parentId;

    @ExcelProperty("机构/部门名称")
    @Schema(description = "机构/部门名称")
    private String departName;

    @ExcelProperty("英文名")
    @Schema(description = "英文名")
    private String departNameEn;

    @ExcelProperty("缩写")
    @Schema(description = "缩写")
    private String departNameAbbr;

    @ExcelProperty("排序")
    @Schema(description = "排序")
    private Integer departOrder;

    @ExcelProperty("描述")
    @Schema(description = "描述")
    private String description;

    @ExcelProperty("机构类别")
    @Schema(description = "机构类别 1公司，2组织机构，3岗位")
    private String orgCategory;

    @ExcelProperty("机构类型")
    @Schema(description = "机构类型 1一级部门 2子部门")
    private String orgType;

    @ExcelProperty("机构编码")
    @Schema(description = "机构编码")
    private String orgCode;

    @ExcelProperty("手机号")
    @Schema(description = "手机号")
    private String mobile;

    @ExcelProperty("传真")
    @Schema(description = "传真")
    private String fax;

    @ExcelProperty("地址")
    @Schema(description = "地址")
    private String address;

    @ExcelProperty("备注")
    @Schema(description = "备注")
    private String memo;

    @ExcelProperty("状态")
    @Schema(description = "状态（1启用，0不启用）")
    private String status;

    @ExcelIgnore
    @Schema(description = "租户ID")
    private String tenantId;

    @ExcelIgnore
    @Schema(description = "是否有叶子节点: 1是0否")
    private Boolean izLeaf;
}
