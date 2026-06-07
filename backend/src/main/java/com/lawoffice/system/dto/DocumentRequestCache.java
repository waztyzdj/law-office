package com.lawoffice.system.dto;

import com.lawoffice.system.entity.SysFileAcl;
import com.lawoffice.system.entity.SysFileRelation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 单次文档中心请求内复用的查询缓存，避免共享权限和业务关系重复查询。
 */
public class DocumentRequestCache {

    private final Map<String, List<SysFileAcl>> activeAclsByFileId = new HashMap<>();

    private final Map<String, Boolean> activeAclFlags = new HashMap<>();

    private List<SysFileRelation> activeBusinessRelations;

    private List<SysFileRelation> accessibleBusinessRelations;

    public Map<String, List<SysFileAcl>> getActiveAclsByFileId() {
        return activeAclsByFileId;
    }

    public Map<String, Boolean> getActiveAclFlags() {
        return activeAclFlags;
    }

    public List<SysFileRelation> getActiveBusinessRelations() {
        return activeBusinessRelations;
    }

    public void setActiveBusinessRelations(List<SysFileRelation> activeBusinessRelations) {
        this.activeBusinessRelations = activeBusinessRelations;
    }

    public List<SysFileRelation> getAccessibleBusinessRelations() {
        return accessibleBusinessRelations;
    }

    public void setAccessibleBusinessRelations(List<SysFileRelation> accessibleBusinessRelations) {
        this.accessibleBusinessRelations = accessibleBusinessRelations;
    }
}
