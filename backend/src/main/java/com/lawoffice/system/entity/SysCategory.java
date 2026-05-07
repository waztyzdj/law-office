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
@TableName("sys_category")
@ModuleInfo(name = "通用类型管理", description = "系统通用类型信息管理")
@Schema(description = "通用类型")
public class SysCategory extends BaseEntity {

    @ExcelProperty("父级节点")
    @Schema(description = "父级节点")
    private String pid;

    @ExcelProperty("类型名称")
    @Schema(description = "类型名称")
    private String name;

    @ExcelProperty("类型编码")
    @Schema(description = "类型编码")
    private String code;

    @ExcelIgnore
    @Schema(description = "是否有子节点")
    private String hasChild;

    @ExcelIgnore
    @Schema(description = "租户ID")
    private String tenantId;
}
