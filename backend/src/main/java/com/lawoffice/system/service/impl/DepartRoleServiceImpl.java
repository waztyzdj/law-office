package com.lawoffice.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.system.entity.DepartRole;
import com.lawoffice.system.entity.DepartRolePermission;
import com.lawoffice.system.entity.DepartRoleUser;
import com.lawoffice.system.entity.Permission;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.mapper.DepartRoleMapper;
import com.lawoffice.system.mapper.DepartRolePermissionMapper;
import com.lawoffice.system.mapper.DepartRoleUserMapper;
import com.lawoffice.system.mapper.PermissionMapper;
import com.lawoffice.system.mapper.UserMapper;
import com.lawoffice.system.service.IDepartRoleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DepartRoleServiceImpl extends BaseServiceImpl<DepartRoleMapper, DepartRole> implements IDepartRoleService {

    @Autowired
    private DepartRolePermissionMapper departRolePermissionMapper;

    @Autowired
    private DepartRoleUserMapper departRoleUserMapper;

    @Autowired
    private PermissionMapper permissionMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(String departRoleId, List<String> permissionIds) {
        // 先删除部门角色现有的所有权限
        LambdaQueryWrapper<DepartRolePermission> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(DepartRolePermission::getRoleId, departRoleId);
        departRolePermissionMapper.delete(deleteWrapper);

        // 批量插入新的权限关联
        if (permissionIds != null && !permissionIds.isEmpty()) {
            List<DepartRolePermission> rolePermissions = permissionIds.stream()
                .map(permissionId -> {
                    DepartRolePermission rolePermission = new DepartRolePermission();
                    rolePermission.setRoleId(departRoleId);
                    rolePermission.setPermissionId(permissionId);
                    return rolePermission;
                })
                .collect(Collectors.toList());

            for (DepartRolePermission rolePermission : rolePermissions) {
                departRolePermissionMapper.insert(rolePermission);
            }
        }

        log.info("为部门角色分配权限成功，部门角色ID: {}, 权限数量: {}", departRoleId, permissionIds == null ? 0 : permissionIds.size());
    }

    @Override
    public List<Permission> getDepartRolePermissions(String departRoleId) {
        // 查询部门角色的权限ID列表
        LambdaQueryWrapper<DepartRolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DepartRolePermission::getRoleId, departRoleId);
        List<DepartRolePermission> rolePermissions = departRolePermissionMapper.selectList(wrapper);

        if (rolePermissions.isEmpty()) {
            return new ArrayList<>();
        }

        // 根据权限ID查询权限详情
        List<String> permissionIds = rolePermissions.stream()
            .map(DepartRolePermission::getPermissionId)
            .collect(Collectors.toList());

        LambdaQueryWrapper<Permission> permissionWrapper = new LambdaQueryWrapper<>();
        permissionWrapper.in(Permission::getId, permissionIds);
        return permissionMapper.selectList(permissionWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removePermissions(String departRoleId, List<String> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<DepartRolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DepartRolePermission::getRoleId, departRoleId)
               .in(DepartRolePermission::getPermissionId, permissionIds);
        departRolePermissionMapper.delete(wrapper);

        log.info("移除部门角色权限成功，部门角色ID: {}, 移除权限数量: {}", departRoleId, permissionIds.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignUsers(String departRoleId, List<String> userIds) {
        // 先删除部门角色现有的所有用户
        LambdaQueryWrapper<DepartRoleUser> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(DepartRoleUser::getDroleId, departRoleId);
        departRoleUserMapper.delete(deleteWrapper);

        // 批量插入新的用户关联
        if (userIds != null && !userIds.isEmpty()) {
            List<DepartRoleUser> roleUsers = userIds.stream()
                .map(userId -> {
                    DepartRoleUser roleUser = new DepartRoleUser();
                    roleUser.setDroleId(departRoleId);
                    roleUser.setUserId(userId);
                    return roleUser;
                })
                .collect(Collectors.toList());

            for (DepartRoleUser roleUser : roleUsers) {
                departRoleUserMapper.insert(roleUser);
            }
        }

        log.info("为部门角色分配用户成功，部门角色ID: {}, 用户数量: {}", departRoleId, userIds == null ? 0 : userIds.size());
    }

    @Override
    public List<User> getDepartRoleUsers(String departRoleId) {
        // 查询部门角色的用户ID列表
        LambdaQueryWrapper<DepartRoleUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DepartRoleUser::getDroleId, departRoleId);
        List<DepartRoleUser> roleUsers = departRoleUserMapper.selectList(wrapper);

        if (roleUsers.isEmpty()) {
            return new ArrayList<>();
        }

        // 根据用户ID查询用户详情
        List<String> userIds = roleUsers.stream()
            .map(DepartRoleUser::getUserId)
            .collect(Collectors.toList());

        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.in(User::getId, userIds);
        return userMapper.selectList(userWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeUsers(String departRoleId, List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<DepartRoleUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DepartRoleUser::getDroleId, departRoleId)
               .in(DepartRoleUser::getUserId, userIds);
        departRoleUserMapper.delete(wrapper);

        log.info("移除部门角色用户成功，部门角色ID: {}, 移除用户数量: {}", departRoleId, userIds.size());
    }
}
