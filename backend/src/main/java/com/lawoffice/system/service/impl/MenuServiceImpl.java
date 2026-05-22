package com.lawoffice.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawoffice.system.constant.PermissionMenuTypes;
import com.lawoffice.system.vo.MenuMetaVO;
import com.lawoffice.system.vo.MenuRouteVO;
import com.lawoffice.system.entity.Permission;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.mapper.UserMapper;
import com.lawoffice.system.service.IMenuService;
import com.lawoffice.system.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 菜单服务实现类
 */
@Slf4j
@Service
public class MenuServiceImpl implements IMenuService {

    private static final Map<String, String> LEGACY_ICON_MAP = Map.ofEntries(
            Map.entry("setting", "lucide:settings"),
            Map.entry("settings", "lucide:settings"),
            Map.entry("user", "lucide:user"),
            Map.entry("users", "lucide:users"),
            Map.entry("role", "lucide:shield-check"),
            Map.entry("shield", "lucide:shield-check"),
            Map.entry("menu", "lucide:menu"),
            Map.entry("dict", "lucide:book-open-text"),
            Map.entry("database", "lucide:database"),
            Map.entry("log", "lucide:scroll-text"),
            Map.entry("department", "lucide:building-2"),
            Map.entry("depart", "lucide:building-2"),
            Map.entry("tenant", "lucide:landmark"),
            Map.entry("category", "lucide:tags")
    );

    @Autowired
    private IUserService userService;
    
    @Autowired
    private UserMapper userMapper;

    @Override
    public List<MenuRouteVO> getUserMenuTree(String username) {
        if (!StringUtils.hasText(username)) {
            throw new RuntimeException("用户名不能为空");
        }
        
        // 根据用户名获取用户信息
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username)
               .eq(User::getDeleteFlag, 0);
        User user = userMapper.selectOne(wrapper);
        
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        // 根据用户ID获取用户权限列表
        List<Permission> permissions = userService.getUserPermissions(user.getId());
        
        // 转换为树形菜单结构
        return buildMenuTree(permissions);
    }
    
    /**
     * 构建菜单树
     */
    private List<MenuRouteVO> buildMenuTree(List<Permission> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return new ArrayList<>();
        }

        // 只处理菜单类型权限：0 一级菜单、1 子菜单。
        List<Permission> menuPermissions = permissions.stream()
                .filter(p -> PermissionMenuTypes.isMenu(p.getMenuType()))
                .collect(Collectors.toList());

        // 找到所有根节点（parentId为null或空）
        List<Permission> rootPermissions = menuPermissions.stream()
                .filter(p -> p.getParentId() == null || p.getParentId().isEmpty())
                .sorted((p1, p2) -> {
                    if (p1.getSortNo() == null) return 1;
                    if (p2.getSortNo() == null) return -1;
                    return p1.getSortNo().compareTo(p2.getSortNo());
                })
                .collect(Collectors.toList());

        // 递归构建菜单树
        return rootPermissions.stream()
                .map(permission -> buildMenuRoute(permission, menuPermissions))
                .collect(Collectors.toList());
    }
    
    /**
     * 递归构建菜单路由
     */
    private MenuRouteVO buildMenuRoute(Permission permission, List<Permission> allPermissions) {
        MenuRouteVO menuRoute = new MenuRouteVO();
        
        // 设置path
        menuRoute.setPath(permission.getUrl());
        
        // 设置name（使用componentName或name）
        menuRoute.setName(permission.getComponentName() != null ? 
                permission.getComponentName() : convertToCamelCase(permission.getName()));
        
        // 设置component
        menuRoute.setComponent(permission.getComponent());
        
        // 设置redirect
        menuRoute.setRedirect(permission.getRedirect());
        
        // 设置meta
        MenuMetaVO meta = buildMenuMeta(permission);
        menuRoute.setMeta(meta);
        
        // 递归设置子菜单
        List<MenuRouteVO> children = allPermissions.stream()
                .filter(p -> permission.getId().equals(p.getParentId()))
                .sorted((p1, p2) -> {
                    if (p1.getSortNo() == null) return 1;
                    if (p2.getSortNo() == null) return -1;
                    return p1.getSortNo().compareTo(p2.getSortNo());
                })
                .map(child -> buildMenuRoute(child, allPermissions))
                .collect(Collectors.toList());
        
        if (!children.isEmpty()) {
            menuRoute.setChildren(children);
        }
        
        return menuRoute;
    }
    
    /**
     * 构建菜单元数据
     */
    private MenuMetaVO buildMenuMeta(Permission permission) {
        MenuMetaVO meta = new MenuMetaVO();
        
        // 标题
        meta.setTitle(permission.getName());
        
        // 图标
        meta.setIcon(normalizeIcon(permission.getIcon()));
        
        // 排序号
        meta.setOrder(permission.getSortNo());
        
        // 是否隐藏
        meta.setHideInMenu(permission.getHidden() != null && permission.getHidden() == 1);
        
        // 是否隐藏tab
        meta.setHideInTab(permission.getHideTab() != null && permission.getHideTab() == 1);
        
        // 是否缓存
        meta.setKeepAlive(permission.getKeepAlive());
        
        // 固定标签页（默认不固定）
        meta.setAffixTab(false);
        
        // 面包屑显示（默认显示）
        meta.setHideInBreadcrumb(false);
        
        // authority需要根据角色权限动态设置，这里暂时留空
        meta.setAuthority(new ArrayList<>());
        
        return meta;
    }
    
    /**
     * 将中文名称转换为驼峰命名
     */
    private String convertToCamelCase(String name) {
        if (name == null || name.isEmpty()) {
            return "Menu";
        }
        
        // 简单处理：移除空格和特殊字符，首字母大写
        StringBuilder result = new StringBuilder();
        boolean nextUpper = true;
        
        for (char c : name.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                if (nextUpper) {
                    result.append(Character.toUpperCase(c));
                    nextUpper = false;
                } else {
                    result.append(Character.toLowerCase(c));
                }
            } else {
                nextUpper = true;
            }
        }
        
        return result.length() > 0 ? result.toString() : "Menu";
    }

    private String normalizeIcon(String icon) {
        if (!StringUtils.hasText(icon)) {
            return null;
        }
        String normalizedIcon = icon.trim();
        if (normalizedIcon.contains(":")) {
            return normalizedIcon;
        }
        return LEGACY_ICON_MAP.getOrDefault(normalizedIcon, "lucide:" + normalizedIcon);
    }
}
