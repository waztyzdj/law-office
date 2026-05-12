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
@TableName("sys_dict")
@Schema(description = "字典")
public class SysDict extends BaseEntity {

    @ExcelProperty("字典名称")
    @Schema(description = "字典名称")
    private String dictName;

    @ExcelProperty("字典编码")
    @Schema(description = "字典编码")
    private String dictCode;

    @ExcelProperty("描述")
    @Schema(description = "描述")
    private String description;

    @ExcelIgnore
    @Schema(description = "租户ID")
    private String tenantId;
}
