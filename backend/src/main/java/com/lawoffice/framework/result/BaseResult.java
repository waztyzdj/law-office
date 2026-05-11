package com.lawoffice.framework.result;

import lombok.Data;

@Data
public class BaseResult<T> {
    private Integer code;
    private String message;
    private T data;

    private BaseResult(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> BaseResult<T> success(T data) {
        return new BaseResult<>(200, "成功", data);
    }

    public static <T> BaseResult<T> success() {
        return new BaseResult<>(200, "成功", null);
    }

    public static <T> BaseResult<T> error(Integer code, String message) {
        return new BaseResult<>(code, message, null);
    }

    public static <T> BaseResult<T> error(String message) {
        return new BaseResult<>(500, message, null);
    }
}
