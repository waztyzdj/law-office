package com.lawoffice.home.constant;

import java.util.Set;

/**
 * 工作台稳定业务取值。
 */
public final class HomeWorkbenchConstants {

    private HomeWorkbenchConstants() {
    }

    public static final String PERMISSION_WORKBENCH_VIEW = "home:workbench:view";
    public static final String PERMISSION_CARD_QUICK_ENTRY = "home:card:quick-entry";
    public static final String PERMISSION_CARD_RECENT = "home:card:recent";
    public static final String PERMISSION_CARD_MANAGE = "home:card:manage";

    public static final String CARD_TODO = "todo";
    public static final String CARD_CC = "cc";
    public static final String CARD_QUICK_ENTRY = "quick-entry";
    public static final String CARD_MESSAGE = "message";
    public static final String CARD_RECENT = "recent";
    public static final String CARD_METRICS = "metrics";
    public static final String CARD_RISK = "risk";

    public static final String STATUS_ENABLED = "enabled";
    public static final String STATUS_DISABLED = "disabled";

    public static final String OWNER_SYSTEM = "system";
    public static final String OWNER_USER = "user";
    public static final String SYSTEM_OWNER_USER_ID = "system";

    public static final String ENTRY_TYPE_MENU = "menu";
    public static final String ENTRY_TYPE_ROUTE = "route";
    public static final String ENTRY_TYPE_LINK = "link";
    public static final String ENTRY_TYPE_ACTION = "action";

    public static final String TARGET_TYPE_ROUTE = "route";
    public static final String TARGET_TYPE_ACTION = "action";

    public static final String SIZE_SMALL = "small";
    public static final String SIZE_MEDIUM = "medium";
    public static final String SIZE_LARGE = "large";
    public static final String SIZE_FULL = "full";

    public static final String RECORD_TYPE_MENU = "menu";
    public static final String RECORD_TYPE_WORKFLOW = "workflow";
    public static final String RECORD_TYPE_DOCUMENT = "document";
    public static final String RECORD_TYPE_MESSAGE = "message";
    public static final String RECORD_TYPE_BUSINESS = "business";

    public static final Set<String> CARD_SIZES = Set.of(SIZE_SMALL, SIZE_MEDIUM, SIZE_LARGE, SIZE_FULL);
    public static final Set<String> STATUSES = Set.of(STATUS_ENABLED, STATUS_DISABLED);
    public static final Set<String> ENTRY_TYPES = Set.of(ENTRY_TYPE_MENU, ENTRY_TYPE_ROUTE, ENTRY_TYPE_LINK, ENTRY_TYPE_ACTION);
    public static final Set<String> TARGET_TYPES = Set.of(TARGET_TYPE_ROUTE, TARGET_TYPE_ACTION);
    public static final Set<String> RECORD_TYPES = Set.of(
            RECORD_TYPE_MENU,
            RECORD_TYPE_WORKFLOW,
            RECORD_TYPE_DOCUMENT,
            RECORD_TYPE_MESSAGE,
            RECORD_TYPE_BUSINESS
    );
}
