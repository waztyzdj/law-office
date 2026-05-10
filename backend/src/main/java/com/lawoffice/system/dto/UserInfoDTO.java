package com.lawoffice.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "用户详细信息")
public class UserInfoDTO {
    
    @Schema(description = "用户ID")
    private String userId;
    
    @Schema(description = "用户名")
    private String username;
    
    @Schema(description = "真实姓名")
    private String realName;
    
    @Schema(description = "角色列表（用于权限控制）")
    private List<String> roles;
    
    @Schema(description = "权限列表（用于权限控制）")
    private List<String> permissions;
    
    @Schema(description = "默认首页路径（可选）")
    private String homePath;
}
