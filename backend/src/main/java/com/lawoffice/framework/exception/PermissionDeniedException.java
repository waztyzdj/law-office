package com.lawoffice.framework.exception;

import lombok.Getter;

/**
 * 权限不足异常
 * 当用户没有访问某个资源的权限时抛出此异常
 */
@Getter
public class PermissionDeniedException extends RuntimeException {
    
    private final String permissionCode;
    
    public PermissionDeniedException(String message, String permissionCode) {
        super(message);
        this.permissionCode = permissionCode;
    }
    
    public PermissionDeniedException(String permissionCode) {
        super("没有权限访问该功能，需要权限: " + permissionCode);
        this.permissionCode = permissionCode;
    }
}
