package com.lawoffice.system.service;

import com.lawoffice.framework.service.IBaseService;
import com.lawoffice.system.entity.Permission;
import com.lawoffice.system.vo.PermissionVO;

import java.util.List;

public interface IPermissionService extends IBaseService<Permission, PermissionVO> {

    /**
     * 获取菜单权限树。
     * @return 菜单权限树
     */
    List<PermissionVO> tree();
}
