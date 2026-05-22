package com.lawoffice.framework.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.framework.dto.TreeDTO;
import com.lawoffice.framework.entity.BaseEntity;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.service.ITreeService;
import com.lawoffice.framework.tree.TreeNode;
import com.lawoffice.framework.util.TreeUtils;
import com.lawoffice.framework.vo.BaseVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;

/**
 * 树形服务基础实现。
 *
 * @param <M> Mapper 类型
 * @param <E> 实体类型
 * @param <V> VO 类型
 */
@Slf4j
public class TreeServiceImpl<M extends BaseMapper<E>, E extends BaseEntity & TreeNode<E>, V extends BaseVO & TreeNode<V>>
        extends BaseServiceImpl<M, E, V> implements ITreeService<E, V> {

    @Override
    public BaseResult<List<V>> tree(TreeDTO<E> treeDTO) {
        try {
            doBeforeTree(treeDTO);

            QueryWrapper<E> wrapper = treeDTO == null ? null : (QueryWrapper<E>) treeDTO.getQueryWrapper();
            if (wrapper == null) {
                wrapper = new QueryWrapper<>();
            }
            wrapper.eq("delete_flag", 0);
            applyTreeOrder(wrapper);

            List<E> entities = baseMapper.selectList(wrapper);
            List<V> nodes = BeanUtil.copyToList(entities, getVoClass());
            List<V> tree = buildTree(nodes);

            doAfterTree(treeDTO, tree);
            return BaseResult.success(tree);
        } catch (Exception e) {
            log.error("树形查询失败", e);
            return BaseResult.error("树形查询失败: " + e.getMessage());
        }
    }

    /**
     * 树形查询前处理。
     */
    protected void doBeforeTree(TreeDTO<E> treeDTO) {
    }

    /**
     * 树形查询后处理。
     */
    protected void doAfterTree(TreeDTO<E> treeDTO, List<V> tree) {
    }

    /**
     * 应用树形查询排序。
     */
    protected void applyTreeOrder(QueryWrapper<E> wrapper) {
    }

    /**
     * 同级节点排序规则。
     */
    protected Comparator<V> treeNodeComparator() {
        return null;
    }

    /**
     * 组装树形结构。
     */
    protected List<V> buildTree(List<V> nodes) {
        return TreeUtils.buildTree(nodes, treeNodeComparator());
    }

    /**
     * 校验父节点存在且未删除。
     */
    protected void validateParentExists(String parentId) {
        if (!StringUtils.hasText(parentId)) {
            return;
        }

        E parent = baseMapper.selectById(parentId);
        if (parent == null || parent.getDeleteFlag() != null && parent.getDeleteFlag() == 1) {
            throw new IllegalArgumentException("父节点不存在或已被删除");
        }
    }

    /**
     * 校验父节点不能为自身或子节点。
     */
    protected void validateParentNotSelfOrDescendant(String id, String parentId) {
        if (!StringUtils.hasText(id) || !StringUtils.hasText(parentId)) {
            return;
        }
        if (id.equals(parentId)) {
            throw new IllegalArgumentException("父节点不能选择自身");
        }

        String currentParentId = parentId;
        while (StringUtils.hasText(currentParentId)) {
            if (id.equals(currentParentId)) {
                throw new IllegalArgumentException("父节点不能选择自身或子节点");
            }

            E parent = baseMapper.selectById(currentParentId);
            if (parent == null || parent.getDeleteFlag() != null && parent.getDeleteFlag() == 1) {
                throw new IllegalArgumentException("父节点不存在或已被删除");
            }
            currentParentId = parent.getParentId();
        }
    }

    /**
     * 删除前校验是否存在未删除子节点。
     */
    protected void validateNoChildrenBeforeDelete(BaseDTO<E> deleteDTO) {
        List<String> ids = resolveDeleteIds(deleteDTO);
        if (ids.isEmpty()) {
            return;
        }

        QueryWrapper<E> wrapper = new QueryWrapper<>();
        wrapper.in("parent_id", ids)
                .eq("delete_flag", 0);
        if (baseMapper.selectCount(wrapper) > 0) {
            throw new IllegalArgumentException("存在子节点，请先删除子节点");
        }
    }

    /**
     * 获取删除ID集合。
     */
    protected List<String> resolveDeleteIds(BaseDTO<E> deleteDTO) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        if (deleteDTO == null) {
            return ids;
        }
        if (StringUtils.hasText(deleteDTO.getId())) {
            ids.add(deleteDTO.getId());
        }
        if (deleteDTO.getDeleteIds() != null) {
            ids.addAll(deleteDTO.getDeleteIds().stream()
                    .filter(StringUtils::hasText)
                    .toList());
        }
        return ids.stream().distinct().toList();
    }
}
