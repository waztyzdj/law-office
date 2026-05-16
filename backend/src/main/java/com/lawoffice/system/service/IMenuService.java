package com.lawoffice.system.service;

import com.lawoffice.system.vo.MenuRouteVO;

import java.util.List;

/**
 * 菜单服务接口
 */
public interface IMenuService {
    
    /**
     * 获取用户的菜单树
     * @param username 用户名
     * @return 菜单树列表
     */
    List<MenuRouteVO> getUserMenuTree(String username);
}
