package com.lawoffice.framework.vo;

import com.lawoffice.framework.tree.TreeNode;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 树形视图对象基类。
 */
@EqualsAndHashCode(callSuper = true)
public class TreeVO<V extends TreeVO<V>> extends BaseVO implements TreeNode<V> {
    private static final long serialVersionUID = 1L;

    /**
     * 父节点ID。
     */
    private String parentId;

    /**
     * 子节点列表。
     */
    private List<V> children;

    @Override
    public String getParentId() {
        return parentId;
    }

    @Override
    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    @Override
    public List<V> getChildren() {
        return children;
    }

    @Override
    public void setChildren(List<V> children) {
        this.children = children;
    }
}
