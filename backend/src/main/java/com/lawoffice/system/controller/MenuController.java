package com.lawoffice.system.controller;

import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.system.vo.MenuRouteVO;
import com.lawoffice.system.service.IMenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/menu")
@Tag(name = "菜单管理", description = "系统菜单路由管理")
@ModuleInfo(value = "menu", name = "菜单管理", description = "系统菜单路由管理")
public class MenuController {

    @Autowired
    private IMenuService menuService;

    /**
     * 获取所有菜单（根据当前用户权限过滤）
     */
    @GetMapping("/all")
    @Operation(summary = "获取所有菜单", description = "获取当前登录用户的菜单列表，返回树形结构")
    public BaseResult<List<MenuRouteVO>> getAllMenus(HttpServletRequest request) {
        try {
            // 从 request 属性中获取用户名（由 JwtAuthFilter 设置）
            String username = (String) request.getAttribute("username");
            
            if (!StringUtils.hasText(username)) {
                return BaseResult.error(401, "未登录或登录已过期");
            }
            
            // 调用 Service 层处理业务逻辑
            List<MenuRouteVO> menuTree = menuService.getUserMenuTree(username);
            
            log.info("获取菜单成功，用户: {}, 菜单数量: {}", username, menuTree.size());
            return BaseResult.success(menuTree);

        } catch (RuntimeException e) {
            log.warn("获取菜单失败：{}", e.getMessage());
            return BaseResult.error(401, e.getMessage());
        } catch (Exception e) {
            log.error("获取菜单异常", e);
            return BaseResult.error(500, "获取菜单失败：" + e.getMessage());
        }
    }
}
