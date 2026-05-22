package com.lawoffice.system.constant;

/**
 * 菜单类型取值以 sql/建表脚本.sql 中 sys_permission.menu_type 字段注释为准。
 */
public final class PermissionMenuTypes {

    public static final int FIRST_LEVEL_MENU = 0;
    public static final int SUB_MENU = 1;
    public static final int BUTTON_PERMISSION = 2;

    private PermissionMenuTypes() {
    }

    public static boolean isMenu(Integer menuType) {
        return menuType != null && (menuType == FIRST_LEVEL_MENU || menuType == SUB_MENU);
    }

    public static boolean isFirstLevelMenu(Integer menuType) {
        return menuType != null && menuType == FIRST_LEVEL_MENU;
    }

    public static boolean isSubMenu(Integer menuType) {
        return menuType != null && menuType == SUB_MENU;
    }

    public static boolean isButtonPermission(Integer menuType) {
        return menuType != null && menuType == BUTTON_PERMISSION;
    }

    public static boolean isValid(Integer menuType) {
        return isMenu(menuType) || isButtonPermission(menuType);
    }
}
