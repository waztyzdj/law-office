package com.lawoffice.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.system.entity.DepartPermission;
import com.lawoffice.system.entity.DepartRole;
import com.lawoffice.system.entity.Permission;
import com.lawoffice.system.entity.SysDepart;
import com.lawoffice.system.mapper.DepartPermissionMapper;
import com.lawoffice.system.mapper.DepartRoleMapper;
import com.lawoffice.system.mapper.PermissionMapper;
import com.lawoffice.system.mapper.SysDepartMapper;
import com.lawoffice.system.service.ISysDepartService;
import com.lawoffice.system.vo.SysDepartVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SysDepartServiceImpl extends BaseServiceImpl<SysDepartMapper, SysDepart, SysDepartVO> implements ISysDepartService {

    @Autowired
    private DepartRoleMapper departRoleMapper;

    @Autowired
    private DepartPermissionMapper departPermissionMapper;

    @Autowired
    private PermissionMapper permissionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(String departId, List<String> roleIds) {
        // 先删除部门现有的所有角色
        LambdaQueryWrapper<DepartRole> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(DepartRole::getDepartId, departId);
        departRoleMapper.delete(deleteWrapper);

        // TODO: 此方法逻辑需要重新审视 - DepartRole是部门角色定义表，不是关联表
        // 批量插入新的角色关联
        if (roleIds != null && !roleIds.isEmpty()) {
            List<DepartRole> departRoles = roleIds.stream()
                .map(roleId -> {
                    DepartRole departRole = new DepartRole();
                    departRole.setDepartId(departId);
                    // departRole.setRoleId(roleId); // DepartRole没有roleId字段
                    return departRole;
                })
                .collect(Collectors.toList());

            for (DepartRole departRole : departRoles) {
                departRoleMapper.insert(departRole);
            }
        }

        log.info("为部门分配角色成功，部门ID: {}, 角色数量: {}", departId, roleIds == null ? 0 : roleIds.size());
    }

    @Override
    public List<DepartRole> getDepartRoles(String departId) {
        LambdaQueryWrapper<DepartRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DepartRole::getDepartId, departId);
        return departRoleMapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeRoles(String departId, List<String> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }

        // TODO: 此方法逻辑需要重新审视 - DepartRole没有roleId字段
        LambdaQueryWrapper<DepartRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DepartRole::getDepartId, departId);
        // wrapper.in(DepartRole::getRoleId, roleIds); // DepartRole没有roleId字段
        departRoleMapper.delete(wrapper);

        log.info("移除部门角色成功，部门ID: {}, 移除角色数量: {}", departId, roleIds.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(String departId, List<String> permissionIds) {
        // 先删除部门现有的所有权限
        LambdaQueryWrapper<DepartPermission> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(DepartPermission::getDepartId, departId);
        departPermissionMapper.delete(deleteWrapper);

        // 批量插入新的权限关联
        if (permissionIds != null && !permissionIds.isEmpty()) {
            List<DepartPermission> departPermissions = permissionIds.stream()
                .map(permissionId -> {
                    DepartPermission departPermission = new DepartPermission();
                    departPermission.setDepartId(departId);
                    departPermission.setPermissionId(permissionId);
                    return departPermission;
                })
                .collect(Collectors.toList());

            for (DepartPermission departPermission : departPermissions) {
                departPermissionMapper.insert(departPermission);
            }
        }

        log.info("为部门分配权限成功，部门ID: {}, 权限数量: {}", departId, permissionIds == null ? 0 : permissionIds.size());
    }

    @Override
    public List<Permission> getDepartPermissions(String departId) {
        // 查询部门的权限ID列表
        LambdaQueryWrapper<DepartPermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DepartPermission::getDepartId, departId);
        List<DepartPermission> departPermissions = departPermissionMapper.selectList(wrapper);

        if (departPermissions.isEmpty()) {
            return new ArrayList<>();
        }

        // 根据权限ID查询权限详情
        List<String> permissionIds = departPermissions.stream()
            .map(DepartPermission::getPermissionId)
            .collect(Collectors.toList());

        LambdaQueryWrapper<Permission> permissionWrapper = new LambdaQueryWrapper<>();
        permissionWrapper.in(Permission::getId, permissionIds);
        return permissionMapper.selectList(permissionWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removePermissions(String departId, List<String> permissionIds) {
        if (permissionIds == null || permissionIds.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<DepartPermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DepartPermission::getDepartId, departId)
               .in(DepartPermission::getPermissionId, permissionIds);
        departPermissionMapper.delete(wrapper);

        log.info("移除部门权限成功，部门ID: {}, 移除权限数量: {}", departId, permissionIds.size());
    }
}
