package com.lawoffice.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "菜单路由信息")
public class MenuRouteDTO {
    
    @Schema(description = "路由路径")
    private String path;
    
    @Schema(description = "路由名称")
    private String name;
    
    @Schema(description = "组件路径")
    private String component;
    
    @Schema(description = "重定向地址")
    private String redirect;
    
    @Schema(description = "元数据")
    private MenuMetaDTO meta;
    
    @Schema(description = "子菜单")
    private List<MenuRouteDTO> children;
}
