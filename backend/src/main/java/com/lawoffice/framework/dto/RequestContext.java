package com.lawoffice.framework.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestContext {
    
    private String username;
    
    private String token;
    
    private String ipAddress;
    
    private String userAgent;
}
