package com.lawoffice.framework.dto;

import com.lawoffice.framework.tree.TreeNode;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 树形服务请求 DTO。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TreeDTO<E extends TreeNode<E>> extends BaseDTO<E> {

    /**
     * 是否包含子节点，默认查询完整树。
     */
    private boolean includeChildren = true;
}
