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
@TableName("sys_dict_item")
@ModuleInfo(name = "字典明细管理", description = "系统字典明细信息管理")
@Schema(description = "字典明细")
public class SysDictItem extends BaseEntity {

    @ExcelProperty("字典ID")
    @Schema(description = "字典id")
    private String dictId;

    @ExcelProperty("字典项文本")
    @Schema(description = "字典项文本")
    private String itemText;

    @ExcelProperty("字典项值")
    @Schema(description = "字典项值")
    private String itemValue;

    @ExcelProperty("描述")
    @Schema(description = "描述")
    private String description;

    @ExcelProperty("排序")
    @Schema(description = "排序")
    private Integer sortOrder;

    @ExcelProperty("状态")
    @Schema(description = "状态（1启用 0不启用）")
    private Integer status;
}
