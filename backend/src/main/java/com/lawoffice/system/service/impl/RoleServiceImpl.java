package com.lawoffice.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.system.entity.Permission;
import com.lawoffice.system.entity.Role;
import com.lawoffice.system.entity.RolePermission;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.entity.UserRole;
import com.lawoffice.system.mapper.PermissionMapper;
import com.lawoffice.system.mapper.RoleMapper;
import com.lawoffice.system.mapper.RolePermissionMapper;
import com.lawoffice.system.mapper.UserMapper;
import com.lawoffice.system.mapper.UserRoleMapper;
import com.lawoffice.system.service.ITokenService;
import com.lawoffice.system.service.IRoleService;
import com.lawoffice.system.service.IUserService;
import com.lawoffice.system.vo.RoleVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RoleServiceImpl extends BaseServiceImpl<RoleMapper, Role, RoleVO> implements IRoleService {

    private static final String SUPER_ADMIN_ROLE_CODE = "ADMIN";
    private static final String TENANT_ADMIN_ROLE_CODE_PREFIX = "ADMIN_";

    @Autowired
    private RolePermissionMapper rolePermissionMapper;

    @Autowired
    private PermissionMapper permissionMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ITokenService tokenService;

    @Autowired
    private IUserService userService;

    @Override
    protected void doBeforeSave(BaseDTO<Role> saveDTO) {
        Role role = saveDTO.getEntity();
        if (role == null) {
            return;
        }

        role.setRoleCode(trimToNull(role.getRoleCode()));
        role.setRoleName(trimToNull(role.getRoleName()));
        role.setDescription(trimToNull(role.getDescription()));

        if (StringUtils.hasText(role.getId())) {
            Role oldRole = baseMapper.selectById(role.getId());
            if (oldRole == null || (oldRole.getDeleteFlag() != null && oldRole.getDeleteFlag() == 1)) {
                throw new IllegalArgumentException("角色不存在或已被删除");
            }
            role.setRoleCode(oldRole.getRoleCode());
        }

        if (!StringUtils.hasText(role.getRoleCode())) {
            throw new IllegalArgumentException("角色编码不能为空");
        }
        if (!StringUtils.hasText(role.getRoleName())) {
            throw new IllegalArgumentException("角色名称不能为空");
        }
        if (!StringUtils.hasText(role.getId()) && role.getRoleCode().startsWith(TENANT_ADMIN_ROLE_CODE_PREFIX)) {
            throw new IllegalArgumentException("自定义角色编码不能以 ADMIN_ 开头");
        }

        validateUniqueRoleCode(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(String roleId, List<String> permissionIds) {
        assignPermissions(roleId, permissionIds, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(String roleId, List<String> permissionIds, String operatorUsername) {
        if (!StringUtils.hasText(roleId)) {
            throw new IllegalArgumentException("角色ID不能为空");
        }

        Role role = baseMapper.selectById(roleId);
        if (role == null || (role.getDeleteFlag() != null && role.getDeleteFlag() == 1)) {
            throw new IllegalArgumentException("角色不存在或已被删除");
        }

        List<String> normalizedPermissionIds = expandPermissionIdsWithAncestors(normalizeIds(permissionIds));
        validatePermissions(normalizedPermissionIds);
        if (!isSuperAdminRole(role)) {
            validateGrantWithinOperatorPermissions(normalizedPermissionIds, operatorUsername);
        }

        // 先删除角色现有的所有权限
        LambdaQueryWrapper<RolePermission> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(RolePermission::getRoleId, roleId)
                .eq(RolePermission::getDeleteFlag, 0);
        rolePermissionMapper.delete(deleteWrapper);

        // 批量插入新的权限关联
        if (!normalizedPermissionIds.isEmpty()) {
            List<RolePermission> rolePermissions = normalizedPermissionIds.stream()
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

        log.info("为角色分配权限成功，角色ID: {}, 权限数量: {}", roleId, normalizedPermissionIds.size());
        forceLogoutUsersByRoleIds(List.of(roleId));
    }

    @Override
    public List<Permission> getRolePermissions(String roleId) {
        // 查询角色的权限ID列表
        LambdaQueryWrapper<RolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RolePermission::getRoleId, roleId)
                .eq(RolePermission::getDeleteFlag, 0);
        List<RolePermission> rolePermissions = rolePermissionMapper.selectList(wrapper);

        if (rolePermissions.isEmpty()) {
            return new ArrayList<>();
        }

        // 根据权限ID查询权限详情
        List<String> permissionIds = rolePermissions.stream()
            .map(RolePermission::getPermissionId)
            .collect(Collectors.toList());

        LambdaQueryWrapper<Permission> permissionWrapper = new LambdaQueryWrapper<>();
        permissionWrapper.in(Permission::getId, permissionIds)
                         .eq(Permission::getDeleteFlag, 0)
                         .orderByAsc(Permission::getSortNo);
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
               .in(RolePermission::getPermissionId, permissionIds)
               .eq(RolePermission::getDeleteFlag, 0);
        rolePermissionMapper.delete(wrapper);

        log.info("移除角色权限成功，角色ID: {}, 移除权限数量: {}", roleId, permissionIds.size());
        forceLogoutUsersByRoleIds(List.of(roleId));
    }

    @Override
    protected void doBeforeDelete(BaseDTO<Role> deleteDTO) {
        List<String> roleIds = getDeleteIds(deleteDTO);
        if (roleIds.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Role::getId, roleIds)
                .eq(Role::getDeleteFlag, 0);
        List<String> protectedRoleCodes = baseMapper.selectList(wrapper).stream()
                .map(Role::getRoleCode)
                .filter(StringUtils::hasText)
                .filter(roleCode -> roleCode.startsWith("ADMIN"))
                .distinct()
                .collect(Collectors.toList());
        if (!protectedRoleCodes.isEmpty()) {
            throw new IllegalArgumentException("ADMIN 前缀角色不允许删除: " + String.join(",", protectedRoleCodes));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    protected void doAfterDelete(BaseDTO<Role> deleteDTO) {
        List<String> roleIds = getDeleteIds(deleteDTO);
        if (roleIds.isEmpty()) {
            return;
        }

        forceLogoutUsersByRoleIds(roleIds);

        LambdaQueryWrapper<RolePermission> rolePermissionWrapper = new LambdaQueryWrapper<>();
        rolePermissionWrapper.in(RolePermission::getRoleId, roleIds)
                .eq(RolePermission::getDeleteFlag, 0);
        rolePermissionMapper.delete(rolePermissionWrapper);

        LambdaQueryWrapper<UserRole> userRoleWrapper = new LambdaQueryWrapper<>();
        userRoleWrapper.in(UserRole::getRoleId, roleIds)
                .eq(UserRole::getDeleteFlag, 0);
        userRoleMapper.delete(userRoleWrapper);
    }

    @Override
    public List<String> getRolePermissionIds(String roleId) {
        if (!StringUtils.hasText(roleId)) {
            throw new IllegalArgumentException("角色ID不能为空");
        }

        LambdaQueryWrapper<RolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RolePermission::getRoleId, roleId)
                .eq(RolePermission::getDeleteFlag, 0);
        return rolePermissionMapper.selectList(wrapper).stream()
                .map(RolePermission::getPermissionId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<String> normalizeIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        return ids.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .collect(Collectors.toList());
    }

    private void validatePermissions(List<String> permissionIds) {
        if (permissionIds.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<Permission> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Permission::getId, permissionIds)
               .eq(Permission::getDeleteFlag, 0);
        long count = permissionMapper.selectCount(wrapper);
        if (count != permissionIds.size()) {
            throw new IllegalArgumentException("包含不存在或已删除的权限");
        }
    }

    private void validateGrantWithinOperatorPermissions(List<String> permissionIds, String operatorUsername) {
        if (permissionIds.isEmpty() || !StringUtils.hasText(operatorUsername)) {
            return;
        }

        Set<String> operatorPerms = userService.getUserPermissionCodesByUsername(operatorUsername).stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        LambdaQueryWrapper<Permission> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Permission::getId, permissionIds)
                .eq(Permission::getDeleteFlag, 0);
        List<String> overLimitPerms = permissionMapper.selectList(wrapper).stream()
                .map(Permission::getPerms)
                .filter(StringUtils::hasText)
                .filter(perms -> !operatorPerms.contains(perms))
                .distinct()
                .collect(Collectors.toList());
        if (!overLimitPerms.isEmpty()) {
            throw new IllegalArgumentException("不能授予超出自身范围的权限: " + String.join(",", overLimitPerms));
        }
    }

    private List<String> expandPermissionIdsWithAncestors(List<String> permissionIds) {
        if (permissionIds.isEmpty()) {
            return permissionIds;
        }

        Set<String> expandedIds = new LinkedHashSet<>(permissionIds);
        for (String permissionId : permissionIds) {
            Permission permission = permissionMapper.selectById(permissionId);
            if (permission == null || (permission.getDeleteFlag() != null && permission.getDeleteFlag() == 1)) {
                throw new IllegalArgumentException("包含不存在或已删除的权限");
            }

            String parentId = permission.getParentId();
            Set<String> visitedParentIds = new LinkedHashSet<>();
            while (StringUtils.hasText(parentId)) {
                if (!visitedParentIds.add(parentId)) {
                    throw new IllegalArgumentException("权限父子关系存在循环");
                }

                Permission parent = permissionMapper.selectById(parentId);
                if (parent == null || (parent.getDeleteFlag() != null && parent.getDeleteFlag() == 1)) {
                    throw new IllegalArgumentException("包含不存在或已删除的父级权限");
                }
                expandedIds.add(parent.getId());
                parentId = parent.getParentId();
            }
        }
        return new ArrayList<>(expandedIds);
    }

    private void validateUniqueRoleCode(Role role) {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Role::getRoleCode, role.getRoleCode())
               .eq(Role::getTenantId, role.getTenantId())
               .eq(Role::getDeleteFlag, 0);
        if (StringUtils.hasText(role.getId())) {
            wrapper.ne(Role::getId, role.getId());
        }
        if (baseMapper.selectCount(wrapper) > 0) {
            throw new IllegalArgumentException("角色编码已存在");
        }
    }

    private boolean isSuperAdminRole(Role role) {
        return role != null && SUPER_ADMIN_ROLE_CODE.equals(role.getRoleCode());
    }

    private List<String> getDeleteIds(BaseDTO<Role> deleteDTO) {
        List<String> ids = new ArrayList<>();
        if (StringUtils.hasText(deleteDTO.getId())) {
            ids.add(deleteDTO.getId());
        }
        if (deleteDTO.getDeleteIds() != null) {
            ids.addAll(deleteDTO.getDeleteIds().stream()
                    .filter(StringUtils::hasText)
                    .collect(Collectors.toList()));
        }
        return ids.stream().distinct().collect(Collectors.toList());
    }

    private void forceLogoutUsersByRoleIds(List<String> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<UserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(UserRole::getRoleId, roleIds)
                .eq(UserRole::getDeleteFlag, 0);
        List<String> userIds = userRoleMapper.selectList(wrapper).stream()
                .map(UserRole::getUserId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (userIds.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.in(User::getId, userIds);
        userMapper.selectList(userWrapper).stream()
                .map(User::getUsername)
                .filter(StringUtils::hasText)
                .distinct()
                .forEach(tokenService::forceLogout);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
