package com.lawoffice.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 字典下拉选项
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "字典下拉选项")
public class DictOptionVO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 显示文本
     */
    @Schema(description = "显示文本")
    private String label;

    /**
     * 选项值
     */
    @Schema(description = "选项值")
    private String value;
}
