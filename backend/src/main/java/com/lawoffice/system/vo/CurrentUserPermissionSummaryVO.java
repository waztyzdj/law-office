package com.lawoffice.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@Schema(description = "当前用户菜单权限摘要")
public class CurrentUserPermissionSummaryVO {

    @Schema(description = "菜单ID")
    private String id;

    @Schema(description = "父级菜单ID")
    private String parentId;

    @Schema(description = "菜单名称")
    private String name;

    @Schema(description = "菜单类型(0:一级菜单;1:子菜单;2:按钮权限)")
    private Integer menuType;

    @Schema(description = "子级菜单权限")
    private List<CurrentUserPermissionSummaryVO> children = new ArrayList<>();
}
