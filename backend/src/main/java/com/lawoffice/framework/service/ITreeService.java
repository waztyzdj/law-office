package com.lawoffice.framework.service;

import com.lawoffice.framework.dto.TreeDTO;
import com.lawoffice.framework.entity.BaseEntity;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.tree.TreeNode;
import com.lawoffice.framework.vo.BaseVO;

import java.util.List;

/**
 * 树形服务接口。
 *
 * @param <E> 实体类型
 * @param <V> VO 类型
 */
public interface ITreeService<E extends BaseEntity & TreeNode<E>, V extends BaseVO & TreeNode<V>>
        extends IBaseService<E, V> {

    /**
     * 查询树形列表。
     */
    BaseResult<List<V>> tree(TreeDTO<E> treeDTO);
}
