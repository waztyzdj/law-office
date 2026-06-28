package com.lawoffice.document.constant;

import java.util.Set;

/**
 * 文档中心使用的存储类型、范围、授权目标和虚拟节点取值。
 */
public final class DocumentCenterConstants {

    public static final String DOCUMENT_STORE_TYPE = "manage";
    public static final String TENANT_SHARED_STORE_TYPE = "tenant_shared";
    public static final String DEPART_SHARED_STORE_TYPE = "depart_shared";
    public static final String SHARED_VIEW_STORE_TYPE = "shared_view";
    public static final String SHARED_OWNER_VIEW_STORE_TYPE = "shared_owner_view";
    public static final String SHARED_BY_ME_STORE_TYPE = "shared_by_me";
    public static final String BUSINESS_VIEW_STORE_TYPE = "business_view";
    public static final String BUSINESS_MODULE_VIEW_STORE_TYPE = "business_module_view";
    public static final String BUSINESS_GROUP_VIEW_STORE_TYPE = "business_group_view";
    public static final String BUSINESS_RECORD_VIEW_STORE_TYPE = "business_record_view";

    public static final String SHARED_OWNER_PREFIX = "so:";
    public static final String BUSINESS_MODULE_PREFIX = "bm:";
    public static final String BUSINESS_GROUP_PREFIX = "bg:";
    public static final String BUSINESS_RECORD_PREFIX = "br:";
    public static final String PERSONAL_SHARED_RELATION_PREFIX = "document_shared:";
    public static final String PERSONAL_BUSINESS_RELATION_PREFIX = "document_business:";
    public static final String DEPART_SHARED_RELATION_BIZ_TYPE = "document_depart_shared";
    public static final Set<String> BUSINESS_DOCUMENT_EXCLUDED_BIZ_TYPES = Set.of("user-avatar");

    public static final Integer PERSONAL_SHARED_RELATION_TYPE = 2;
    public static final Integer PERSONAL_BUSINESS_RELATION_TYPE = 3;
    public static final Integer DEPART_SHARED_RELATION_TYPE = 4;

    public static final String SCOPE_ALL = "all";
    public static final String SCOPE_MY = "my";
    public static final String SCOPE_STARRED = "starred";
    public static final String SCOPE_BUSINESS = "business";
    public static final String SCOPE_SHARED = "shared";
    public static final String SCOPE_SHARED_BY_ME = "sharedByMe";
    public static final String SCOPE_TRASH = "trash";

    public static final String TARGET_USER = "user";
    public static final String TARGET_DEPART = "depart";
    public static final String TARGET_ROLE = "role";
    public static final String TARGET_TENANT = "tenant";
    public static final String ALL_ACLS_CACHE_KEY = "__all__";

    public static final String PERMISSION_READ = "read";
    public static final String PERMISSION_DOWNLOAD = "download";
    public static final String PERMISSION_UPDATE = "update";
    public static final String PERMISSION_MANAGE = "manage";
    public static final String VERSION_TYPE_UPLOAD = "upload";

    public static final int TREE_PREFETCH_PARENT_LIMIT = 100;
    public static final int TREE_PREFETCH_PAGE_SIZE = 500;
    public static final int TREE_PREFETCH_MAX_PAGE_NUM = 20;

    private DocumentCenterConstants() {
    }
}
