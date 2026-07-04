package com.lawoffice.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "菜单元数据")
public class MenuMetaVO {

    @Schema(description = "菜单ID")
    private String id;
    
    @Schema(description = "菜单标题")
    private String title;
    
    @Schema(description = "图标")
    private String icon;
    
    @Schema(description = "排序号")
    private Integer order;
    
    @Schema(description = "是否在菜单中隐藏")
    private Boolean hideInMenu;
    
    @Schema(description = "是否在面包屑中隐藏")
    private Boolean hideInBreadcrumb;
    
    @Schema(description = "是否在标签页中隐藏")
    private Boolean hideInTab;
    
    @Schema(description = "是否固定标签页")
    private Boolean affixTab;
    
    @Schema(description = "是否缓存页面")
    private Boolean keepAlive;
    
    @Schema(description = "允许访问的角色列表")
    private List<String> authority;

    @Schema(description = "菜单权限编码")
    private String perms;
}
