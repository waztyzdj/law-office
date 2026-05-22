package com.lawoffice.framework.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.lawoffice.framework.tree.TreeNode;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 多租户树形基础实体。
 */
@EqualsAndHashCode(callSuper = true)
public class TreeTenantEntity<E extends TreeTenantEntity<E>> extends BaseTenantEntity implements TreeNode<E> {

    /**
     * 父节点ID。
     */
    private String parentId;

    /**
     * 子节点列表，仅用于内存组树，不参与数据库持久化。
     */
    @TableField(exist = false)
    private transient List<E> children;

    @Override
    public String getParentId() {
        return parentId;
    }

    @Override
    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    @Override
    public List<E> getChildren() {
        return children;
    }

    @Override
    public void setChildren(List<E> children) {
        this.children = children;
    }
}
