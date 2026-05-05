package com.lawoffice.framework.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum LogType {
    LOGIN(1, "登录日志"),
    OPERATION(2, "操作日志"),
    TENANT_OPERATION(3, "租户操作日志");

    private final Integer code;
    private final String description;
}
