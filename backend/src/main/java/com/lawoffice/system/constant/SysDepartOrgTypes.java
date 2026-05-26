package com.lawoffice.system.constant;

import java.util.Set;

/**
 * 组织机构类型取值以 sql/建表脚本.sql 中 sys_depart.org_type 字段注释为准。
 */
public final class SysDepartOrgTypes {

    public static final String COMPANY = "1";
    public static final String BRANCH = "2";
    public static final String SUBSIDIARY = "3";
    public static final String ORGANIZATION = "4";
    public static final String DEPARTMENT = "5";
    public static final String SUB_DEPARTMENT = "6";
    public static final String GROUP = "7";
    public static final String POSITION = "8";

    private static final Set<String> VALID_TYPES = Set.of(
            COMPANY,
            BRANCH,
            SUBSIDIARY,
            ORGANIZATION,
            DEPARTMENT,
            SUB_DEPARTMENT,
            GROUP,
            POSITION
    );

    private SysDepartOrgTypes() {
    }

    public static boolean isValid(String orgType) {
        return VALID_TYPES.contains(orgType);
    }
}
