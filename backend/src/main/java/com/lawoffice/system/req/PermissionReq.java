package com.lawoffice.system.req;

import com.lawoffice.framework.req.BaseReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 菜单权限请求对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PermissionReq extends BaseReq {
    private static final long serialVersionUID = 1L;

    /**
     * 父级ID
     */
    private String parentId;

    /**
     * 菜单标题
     */
    private String name;

    /**
     * 菜单路径
     */
    private String url;

    /**
     * 组件路径
     */
    private String component;

    /**
     * 组件名称
     */
    private String componentName;

    /**
     * 图标
     */
    private String icon;

    /**
     * 排序号
     */
    private Integer sortNo;

    /**
     * 菜单类型(0:一级菜单; 1:子菜单; 2:按钮权限)
     */
    private Integer menuType;

    /**
     * 权限编码
     */
    private String perms;

    /**
     * 是否隐藏(0-显示,1-隐藏)
     */
    private Integer hidden;

    /**
     * 隐藏Tab
     */
    private Integer hideTab;

    /**
     * 是否缓存
     */
    private Boolean keepAlive;

    /**
     * 重定向地址
     */
    private String redirect;

    /**
     * 状态(1-正常,0-冻结)
     */
    private Integer status;

    /**
     * 删除标识(0-正常,1-已删除)
     */
    private Integer deleteFlag;
}
