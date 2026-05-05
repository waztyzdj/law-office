package com.lawoffice.framework.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OperateType {
    QUERY(1, "查询"),
    GET_BY_ID(2, "根据ID查询"),
    SAVE(3, "保存"),
    BATCH_SAVE(4, "批量保存"),
    DELETE(5, "删除"),
    BATCH_DELETE(6, "批量删除"),
    EXPORT(7, "导出"),
    IMPORT(8, "导入"),
    CUSTOM(99, "自定义");

    private final Integer code;
    private final String description;
}
