package com.lawoffice.system.util;

import com.lawoffice.system.entity.Permission;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 菜单树形结构构建工具类
 */
public class MenuTreeUtils {

    /**
     * 将扁平的菜单列表转换为树形结构
     * @param menuList 扁平的菜单列表
     * @return 树形结构的菜单列表
     */
    public static List<Permission> buildMenuTree(List<Permission> menuList) {
        if (menuList == null || menuList.isEmpty()) {
            return new ArrayList<>();
        }

        // 找到所有根节点（parentId为null或空的节点）
        List<Permission> rootMenus = menuList.stream()
                .filter(menu -> menu.getParentId() == null || menu.getParentId().isEmpty())
                .sorted((m1, m2) -> {
                    if (m1.getSortNo() == null) return 1;
                    if (m2.getSortNo() == null) return -1;
                    return m1.getSortNo().compareTo(m2.getSortNo());
                })
                .collect(Collectors.toList());

        // 为每个根节点递归构建子树
        for (Permission rootMenu : rootMenus) {
            List<Permission> children = buildChildren(rootMenu.getId(), menuList);
            // 注意：Permission实体中没有children字段，这里只返回扁平列表
            // 如果需要树形结构，前端可以根据parentId自行构建
        }

        return rootMenus;
    }

    /**
     * 递归构建子菜单
     * @param parentId 父节点ID
     * @param allMenus 所有菜单列表
     * @return 子菜单列表
     */
    private static List<Permission> buildChildren(String parentId, List<Permission> allMenus) {
        List<Permission> children = allMenus.stream()
                .filter(menu -> parentId.equals(menu.getParentId()))
                .sorted((m1, m2) -> {
                    if (m1.getSortNo() == null) return 1;
                    if (m2.getSortNo() == null) return -1;
                    return m1.getSortNo().compareTo(m2.getSortNo());
                })
                .collect(Collectors.toList());

        // 递归处理每个子节点
        for (Permission child : children) {
            List<Permission> grandChildren = buildChildren(child.getId(), allMenus);
            // 注意：Permission实体中没有children字段
        }

        return children;
    }

    /**
     * 按层级排序菜单（不构建树形结构，只是排序）
     * @param menuList 菜单列表
     * @return 排序后的菜单列表
     */
    public static List<Permission> sortMenusByHierarchy(List<Permission> menuList) {
        if (menuList == null || menuList.isEmpty()) {
            return new ArrayList<>();
        }

        return menuList.stream()
                .sorted((m1, m2) -> {
                    // 先按parentId排序
                    if (m1.getParentId() == null && m2.getParentId() != null) {
                        return -1;
                    }
                    if (m1.getParentId() != null && m2.getParentId() == null) {
                        return 1;
                    }
                    
                    // 再按sortNo排序
                    if (m1.getSortNo() == null) return 1;
                    if (m2.getSortNo() == null) return -1;
                    return m1.getSortNo().compareTo(m2.getSortNo());
                })
                .collect(Collectors.toList());
    }
}
