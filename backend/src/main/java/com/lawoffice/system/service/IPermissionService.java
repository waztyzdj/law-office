package com.lawoffice.system.service;

import com.lawoffice.framework.service.ITreeService;
import com.lawoffice.system.entity.Permission;
import com.lawoffice.system.vo.PermissionVO;

import java.util.List;

public interface IPermissionService extends ITreeService<Permission, PermissionVO> {

    /**
     * 获取菜单权限树。
     * @return 菜单权限树
     */
    List<PermissionVO> tree();
}
