package com.lawoffice.framework.util;

import com.lawoffice.framework.tree.TreeNode;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 树形数据工具。
 */
public final class TreeUtils {

    private TreeUtils() {
    }

    /**
     * 将扁平节点列表组装为树。
     *
     * @param nodes 节点列表
     * @param comparator 同级排序规则
     * @return 树形节点列表
     * @param <T> 节点类型
     */
    public static <T extends TreeNode<T>> List<T> buildTree(List<T> nodes, Comparator<T> comparator) {
        if (nodes == null || nodes.isEmpty()) {
            return new ArrayList<>();
        }

        nodes.forEach(node -> node.setChildren(null));

        Map<String, List<T>> childrenMap = nodes.stream()
                .filter(node -> StringUtils.hasText(node.getParentId()))
                .collect(Collectors.groupingBy(TreeNode::getParentId));

        nodes.forEach(node -> {
            List<T> children = childrenMap.get(node.getId());
            if (children != null && !children.isEmpty()) {
                sort(children, comparator);
                node.setChildren(children);
            }
        });

        List<T> roots = nodes.stream()
                .filter(node -> !StringUtils.hasText(node.getParentId()))
                .collect(Collectors.toCollection(ArrayList::new));
        sort(roots, comparator);
        return roots;
    }

    /**
     * 收集节点及其全部子节点ID。
     */
    public static <T extends TreeNode<T>> List<String> collectSelfAndDescendantIds(List<T> nodes, String id) {
        if (!StringUtils.hasText(id) || nodes == null || nodes.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, T> nodeMap = nodes.stream()
                .filter(node -> StringUtils.hasText(node.getId()))
                .collect(Collectors.toMap(TreeNode::getId, Function.identity(), (left, right) -> left));
        T target = nodeMap.get(id);
        if (target == null) {
            return new ArrayList<>();
        }

        List<T> tree = buildTree(nodes, null);
        T treeTarget = findNode(tree, id);
        List<String> ids = new ArrayList<>();
        collectIds(treeTarget, ids);
        return ids;
    }

    private static <T> void sort(List<T> list, Comparator<T> comparator) {
        if (comparator != null) {
            list.sort(comparator);
        }
    }

    private static <T extends TreeNode<T>> T findNode(List<T> nodes, String id) {
        for (T node : nodes) {
            if (id.equals(node.getId())) {
                return node;
            }
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                T match = findNode(node.getChildren(), id);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static <T extends TreeNode<T>> void collectIds(T node, List<String> ids) {
        if (node == null) {
            return;
        }
        if (StringUtils.hasText(node.getId())) {
            ids.add(node.getId());
        }
        if (node.getChildren() == null || node.getChildren().isEmpty()) {
            return;
        }
        node.getChildren().forEach(child -> collectIds(child, ids));
    }
}
