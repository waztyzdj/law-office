package com.lawoffice.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.system.entity.Permission;
import com.lawoffice.system.entity.Role;
import com.lawoffice.system.entity.RolePermission;
import com.lawoffice.system.mapper.PermissionMapper;
import com.lawoffice.system.mapper.RoleMapper;
import com.lawoffice.system.mapper.RolePermissionMapper;
import com.lawoffice.system.service.IRoleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RoleServiceImpl extends BaseServiceImpl<RoleMapper, Role> implements IRoleService {

    @Autowired
    private RolePermissionMapper rolePermissionMapper;

    @Autowired
    private PermissionMapper permissionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(String roleId, List<String> permissionIds) {
        // 先删除角色现有的所有权限
        LambdaQueryWrapper<RolePermission> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(RolePermission::getRoleId, roleId);
        rolePermissionMapper.delete(deleteWrapper);

        // 批量插入新的权限关联
        if (permissionIds != null && !permissionIds.isEmpty()) {
            List<RolePermission> rolePermissions = permissionIds.stream()
                .map(permissionId -> {
                    RolePermission rolePermission = new RolePermission();
                    rolePermission.setRoleId(roleId);
                    rolePermission.setPermissionId(permissionId);
                    return rolePermission;
                })
                .collect(Collectors.toList());

            for (RolePermission rolePermission : rolePermissions) {
                rolePermissionMapper.insert(rolePermission);
            }
        }

        log.info("为角色分配权限成功，角色ID: {}, 权限数量: {}", roleId, permissionIds == null ? 0 : permissionIds.size());
    }

    @Override
    public List<Permission> getRolePermissions(String roleId) {
        // 查询角色的权限ID列表
        LambdaQueryWrapper<RolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RolePermission::getRoleId, roleId);
        List<RolePermission> rolePermissions = rolePermissionMapper.selectList(wrapper);

        if (rolePermissions.isEmpty()) {
            return new ArrayList<>();
        }

        // 根据权限ID查询权限详情
        List<String> permissionIds = rolePermissions.stream()
            .map(RolePermission::getPermissionId)
            .collect(Collectors.toList());

        LambdaQueryWrapper<Permission> permissionWrapper = new LambdaQueryWrapper<>();
        permissionWrapper.in(Permission::getId, permissionIds);
        return permissionMapper.selectList(permissionWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removePermissions(String roleId, List<String> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<RolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RolePermission::getRoleId, roleId)
               .in(RolePermission::getPermissionId, permissionIds);
        rolePermissionMapper.delete(wrapper);

        log.info("移除角色权限成功，角色ID: {}, 移除权限数量: {}", roleId, permissionIds.size());
    }
}
