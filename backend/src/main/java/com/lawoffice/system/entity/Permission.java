package com.lawoffice.system.entity;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.ExcelIgnore;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.entity.BaseEntity;
import com.lawoffice.framework.annotation.ModuleInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_permission")
@ModuleInfo(value = "permission", name = "菜单权限管理", description = "系统菜单权限信息管理")
@Schema(description = "菜单权限")
public class Permission extends BaseEntity {

    @ExcelProperty("父级ID")
    @Schema(description = "父id")
    private String parentId;

    @ExcelProperty("菜单标题")
    @Schema(description = "菜单标题")
    private String name;

    @ExcelProperty("路径")
    @Schema(description = "路径")
    private String url;

    @ExcelProperty("组件")
    @Schema(description = "组件")
    private String component;

    @ExcelIgnore
    @Schema(description = "是否路由菜单: 0:不是  1:是（默认值1）")
    private Boolean isRoute;

    @ExcelProperty("组件名字")
    @Schema(description = "组件名字")
    private String componentName;

    @ExcelProperty("一级菜单跳转地址")
    @Schema(description = "一级菜单跳转地址")
    private String redirect;

    @ExcelProperty("菜单类型")
    @Schema(description = "菜单类型(0:一级菜单; 1:子菜单:2:按钮权限)")
    private Integer menuType;

    @ExcelProperty("菜单权限编码")
    @Schema(description = "菜单权限编码")
    private String perms;

    @ExcelIgnore
    @Schema(description = "权限策略1显示2禁用")
    private String permsType;

    @ExcelProperty("菜单排序")
    @Schema(description = "菜单排序")
    private Double sortNo;

    @ExcelIgnore
    @Schema(description = "聚合子路由: 1是0否")
    private Boolean alwaysShow;

    @ExcelProperty("菜单图标")
    @Schema(description = "菜单图标")
    private String icon;

    @ExcelIgnore
    @Schema(description = "是否叶子节点:    1是0否")
    private Boolean isLeaf;

    @ExcelIgnore
    @Schema(description = "是否缓存该页面:    1:是   0:不是")
    private Boolean keepAlive;

    @ExcelIgnore
    @Schema(description = "是否隐藏路由: 0否,1是")
    private Integer hidden;

    @ExcelIgnore
    @Schema(description = "是否隐藏tab: 0否,1是")
    private Integer hideTab;

    @ExcelProperty("描述")
    @Schema(description = "描述")
    private String description;

    @ExcelIgnore
    @Schema(description = "是否添加数据权限1是0否")
    private Integer ruleFlag;

    @ExcelIgnore
    @Schema(description = "按钮权限状态(0无效1有效)")
    private String status;

    @ExcelIgnore
    @Schema(description = "外链菜单打开方式 0/内部打开 1/外部打开")
    private Boolean internalOrExternal;
}
