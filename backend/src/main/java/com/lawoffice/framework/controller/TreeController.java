package com.lawoffice.framework.controller;

import com.lawoffice.framework.dto.TreeDTO;
import com.lawoffice.framework.entity.BaseEntity;
import com.lawoffice.framework.req.BaseQueryReq;
import com.lawoffice.framework.req.TreeReq;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.service.ITreeService;
import com.lawoffice.framework.tree.TreeNode;
import com.lawoffice.framework.util.QueryWrapperBuilderUtils;
import com.lawoffice.framework.vo.BaseVO;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

/**
 * 树形控制器基类。
 */
public class TreeController<S extends ITreeService<E, V>, E extends BaseEntity & TreeNode<E>, V extends BaseVO & TreeNode<V>, R extends TreeReq>
        extends BaseController<S, E, V, R> {

    /**
     * 树形查询。
     */
    @GetMapping("/tree")
    @Operation(summary = "树形查询", description = "查询树形数据")
    public BaseResult<List<V>> tree(HttpServletRequest request, HttpServletResponse response) {
        TreeDTO<E> treeDTO = new TreeDTO<>();
        initBaseDTO(treeDTO, request, response);
        return baseService.tree(treeDTO);
    }

    /**
     * 树形查询，支持动态查询条件。
     */
    @PostMapping("/tree")
    @Operation(summary = "树形查询", description = "查询树形数据，支持动态查询条件")
    public BaseResult<List<V>> tree(
            @RequestBody(required = false) BaseQueryReq req,
            HttpServletRequest request,
            HttpServletResponse response) {
        TreeDTO<E> treeDTO = new TreeDTO<>();
        initBaseDTO(treeDTO, request, response);
        if (req != null) {
            treeDTO.setQueryWrapper(QueryWrapperBuilderUtils.build(req));
        }
        return baseService.tree(treeDTO);
    }
}
