package com.lawoffice.framework.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestContext {
    
    private String username;

    private String userId;
    
    private String token;
    
    private String ipAddress;
    
    private String userAgent;
    
    private String tenantId;  // 当前租户ID

    private transient Set<String> permissionCodes;
}
