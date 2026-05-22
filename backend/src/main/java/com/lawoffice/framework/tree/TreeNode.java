package com.lawoffice.framework.tree;

import java.util.List;

/**
 * 树节点通用契约。
 *
 * @param <T> 节点类型
 */
public interface TreeNode<T extends TreeNode<T>> {

    /**
     * 节点ID。
     */
    String getId();

    /**
     * 父节点ID。
     */
    String getParentId();

    /**
     * 设置父节点ID。
     */
    void setParentId(String parentId);

    /**
     * 子节点列表。
     */
    List<T> getChildren();

    /**
     * 设置子节点列表。
     */
    void setChildren(List<T> children);
}
