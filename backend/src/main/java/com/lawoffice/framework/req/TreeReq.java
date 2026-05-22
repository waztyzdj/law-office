package com.lawoffice.framework.req;

/**
 * 树形请求对象基类。
 */
public class TreeReq extends BaseReq {
    private static final long serialVersionUID = 1L;

    /**
     * 父节点ID。
     */
    private String parentId;

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }
}
