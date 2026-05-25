package com.lawoffice.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawoffice.framework.config.TenantContextHolder;
import com.lawoffice.system.entity.Role;
import com.lawoffice.system.entity.RolePermission;
import com.lawoffice.system.entity.Tenant;
import com.lawoffice.system.entity.UserRole;
import com.lawoffice.system.entity.UserTenant;
import com.lawoffice.system.mapper.RoleMapper;
import com.lawoffice.system.mapper.RolePermissionMapper;
import com.lawoffice.system.mapper.TenantMapper;
import com.lawoffice.system.mapper.UserRoleMapper;
import com.lawoffice.system.mapper.UserTenantMapper;
import com.lawoffice.system.service.ITenantDefaultDataSyncService;
import com.lawoffice.system.service.ITenantLifecycleService;
import com.lawoffice.util.EntityFillUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TenantLifecycleServiceImpl implements ITenantLifecycleService {

    private static final String TENANT_ADMIN_ROLE_NAME_SUFFIX = "管理员";
    private static final String TENANT_ADMIN_ROLE_DESCRIPTION = "租户默认管理员角色";
    private static final String TENANT_ADMIN_ROLE_CODE_PREFIX = "ADMIN_";
    private static final String TENANT_USER_STATUS_NORMAL = "1";

    private final TenantMapper tenantMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserTenantMapper userTenantMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final ITenantDefaultDataSyncService tenantDefaultDataSyncService;

    public TenantLifecycleServiceImpl(
            TenantMapper tenantMapper,
            RoleMapper roleMapper,
            UserRoleMapper userRoleMapper,
            UserTenantMapper userTenantMapper,
            RolePermissionMapper rolePermissionMapper,
            ITenantDefaultDataSyncService tenantDefaultDataSyncService) {
        this.tenantMapper = tenantMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.userTenantMapper = userTenantMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.tenantDefaultDataSyncService = tenantDefaultDataSyncService;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void initializeTenant(Tenant tenant, List<String> adminUserIds, String operator, LocalDateTime operateTime) {
        if (tenant == null || !StringUtils.hasText(tenant.getId())) {
            return;
        }

        String tenantId = tenant.getId();
        String resolvedOperator = resolveOperator(operator);
        LocalDateTime resolvedOperateTime = operateTime != null ? operateTime : LocalDateTime.now();

        // 租户保存后立即完成初始化，避免新租户缺少管理员角色或基础字典导致无法登录使用。
        ensureTenantAdminRole(tenantId, resolvedOperator, resolvedOperateTime);
        tenantDefaultDataSyncService.syncDefaultDataToTenant(tenantId, resolvedOperator);

        if (adminUserIds != null && !adminUserIds.isEmpty()) {
            assignTenantAdmins(tenantId, adminUserIds, resolvedOperator);
        }
    }

    @Override
    public List<String> getTenantUserIds(String tenantId) {
        validateTenantId(tenantId);

        LambdaQueryWrapper<UserTenant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserTenant::getTenantId, tenantId)
                .eq(UserTenant::getStatus, TENANT_USER_STATUS_NORMAL)
                .eq(UserTenant::getDeleteFlag, 0);
        return userTenantMapper.selectList(wrapper).stream()
                .map(UserTenant::getUserId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void assignTenantUsers(String tenantId, List<String> userIds, String operator) {
        validateTenantId(tenantId);
        syncTenantUsers(tenantId, userIds, resolveOperator(operator));
    }

    @Override
    public List<String> getTenantAdminUserIds(String tenantId) {
        validateTenantId(tenantId);

        Role adminRole = ensureTenantAdminRole(tenantId, "system", LocalDateTime.now());
        return runWithTenant(tenantId, () -> {
            LambdaQueryWrapper<UserRole> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(UserRole::getRoleId, adminRole.getId())
                    .eq(UserRole::getDeleteFlag, 0);
            return userRoleMapper.selectList(wrapper).stream()
                    .map(UserRole::getUserId)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .collect(Collectors.toList());
        });
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void assignTenantAdmins(String tenantId, List<String> userIds, String operator) {
        validateTenantId(tenantId);
        String resolvedOperator = resolveOperator(operator);

        // 管理员必须同时是租户成员，先补齐成员关系再同步默认管理员角色。
        ensureTenantUsers(tenantId, userIds);
        Role adminRole = ensureTenantAdminRole(tenantId, resolvedOperator, LocalDateTime.now());
        runWithTenant(tenantId, () -> {
            syncTenantAdminUsers(adminRole.getId(), userIds, resolvedOperator);
            return null;
        });
    }

    @Override
    public Role ensureTenantAdminRole(String tenantId, String operator, LocalDateTime operateTime) {
        Tenant tenant = getExistingTenant(tenantId);
        String resolvedOperator = resolveOperator(operator);
        LocalDateTime resolvedOperateTime = operateTime != null ? operateTime : LocalDateTime.now();

        return runWithTenant(tenantId, () -> {
            String roleCode = buildTenantAdminRoleCode(tenantId);
            String roleName = buildTenantAdminRoleName(tenant);
            LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Role::getTenantId, tenantId)
                    .eq(Role::getRoleCode, roleCode)
                    .last("LIMIT 1");

            Role existingRole = roleMapper.selectOne(wrapper);
            if (existingRole != null) {
                restoreTenantAdminRoleIfNeeded(existingRole, roleCode, roleName, resolvedOperator, resolvedOperateTime);
                return existingRole;
            }

            Role role = new Role();
            role.setId(UUID.randomUUID().toString().replace("-", ""));
            role.setTenantId(tenantId);
            role.setRoleCode(roleCode);
            role.setRoleName(roleName);
            role.setDescription(TENANT_ADMIN_ROLE_DESCRIPTION);
            role.setCreateBy(resolvedOperator);
            role.setCreateTime(resolvedOperateTime);
            role.setDeleteFlag(0);
            roleMapper.insert(role);
            log.info("创建租户默认管理员角色成功，租户ID: {}, 角色ID: {}", tenantId, role.getId());
            return role;
        });
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void deleteTenantRelations(String tenantId, String deleteBy) {
        validateTenantId(tenantId);

        String resolvedDeleteBy = resolveOperator(deleteBy);
        List<String> roleIds = getTenantRoleIds(tenantId);
        runWithTenant(tenantId, () -> {
            deleteTenantRolePermissions(tenantId, roleIds, resolvedDeleteBy);
            deleteTenantUserRoleRelations(tenantId, roleIds, resolvedDeleteBy);

            LambdaQueryWrapper<Role> roleWrapper = new LambdaQueryWrapper<>();
            roleWrapper.eq(Role::getTenantId, tenantId)
                    .eq(Role::getDeleteFlag, 0);
            Role role = new Role();
            EntityFillUtils.fillDeleteFields(role, resolvedDeleteBy);
            roleMapper.update(role, roleWrapper);
            return null;
        });

        LambdaQueryWrapper<UserTenant> userTenantWrapper = new LambdaQueryWrapper<>();
        userTenantWrapper.eq(UserTenant::getTenantId, tenantId)
                .eq(UserTenant::getDeleteFlag, 0);
        softDeleteUserTenants(userTenantWrapper, resolvedDeleteBy);
    }

    private void restoreTenantAdminRoleIfNeeded(
            Role existingRole,
            String roleCode,
            String roleName,
            String operator,
            LocalDateTime operateTime) {
        boolean needUpdate = !roleCode.equals(existingRole.getRoleCode())
                || !roleName.equals(existingRole.getRoleName())
                || !TENANT_ADMIN_ROLE_DESCRIPTION.equals(existingRole.getDescription())
                || existingRole.getDeleteFlag() == null
                || existingRole.getDeleteFlag() != 0;
        if (!needUpdate) {
            return;
        }

        existingRole.setRoleCode(roleCode);
        existingRole.setRoleName(roleName);
        existingRole.setDescription(TENANT_ADMIN_ROLE_DESCRIPTION);
        existingRole.setDeleteFlag(0);
        existingRole.setDeleteTime(null);
        existingRole.setDeleteBy(null);
        existingRole.setUpdateBy(operator);
        existingRole.setUpdateTime(operateTime);
        roleMapper.updateById(existingRole);
    }

    /**
     * 覆盖同步租户默认管理员角色下的用户成员。
     */
    private void syncTenantAdminUsers(String roleId, List<String> targetUserIds, String operator) {
        LambdaQueryWrapper<UserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserRole::getRoleId, roleId)
                .eq(UserRole::getDeleteFlag, 0);
        List<UserRole> existingRelations = userRoleMapper.selectList(wrapper);

        Set<String> targetUserIdSet = new LinkedHashSet<>(targetUserIds);
        Set<String> existingUserIdSet = existingRelations.stream()
                .map(UserRole::getUserId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> deleteIds = existingRelations.stream()
                .filter(relation -> StringUtils.hasText(relation.getUserId()))
                .filter(relation -> !targetUserIdSet.contains(relation.getUserId()))
                .map(UserRole::getId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());

        if (!deleteIds.isEmpty()) {
            // 管理员成员采用覆盖保存，取消选择的用户只移出默认管理员角色。
            LambdaQueryWrapper<UserRole> deleteWrapper = new LambdaQueryWrapper<>();
            deleteWrapper.in(UserRole::getId, deleteIds);
            softDeleteUserRoles(deleteWrapper, operator);
        }

        for (String userId : targetUserIdSet) {
            if (existingUserIdSet.contains(userId)) {
                continue;
            }
            UserRole userRole = new UserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            userRoleMapper.insert(userRole);
        }
    }

    /**
     * 设置租户管理员前，先确保这些用户都是该租户成员。
     */
    private void ensureTenantUsers(String tenantId, List<String> userIds) {
        for (String userId : userIds) {
            upsertUserTenantRelation(userId, tenantId);
        }
    }

    /**
     * 以租户为主维度差量同步租户成员。
     */
    private void syncTenantUsers(String tenantId, List<String> targetUserIds, String operator) {
        LambdaQueryWrapper<UserTenant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserTenant::getTenantId, tenantId);
        List<UserTenant> existingRelations = userTenantMapper.selectList(wrapper);

        Set<String> targetUserIdSet = new LinkedHashSet<>(targetUserIds);
        Set<String> existingNormalUserIdSet = existingRelations.stream()
                .filter(relation -> relation.getDeleteFlag() == null || relation.getDeleteFlag() == 0)
                .filter(relation -> TENANT_USER_STATUS_NORMAL.equals(relation.getStatus()))
                .map(UserTenant::getUserId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<UserTenant> relationsToDelete = existingRelations.stream()
                .filter(relation -> StringUtils.hasText(relation.getUserId()))
                .filter(relation -> !targetUserIdSet.contains(relation.getUserId()))
                .collect(Collectors.toList());
        List<String> deleteIds = relationsToDelete.stream()
                .map(UserTenant::getId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        List<String> deleteUserIds = relationsToDelete.stream()
                .map(UserTenant::getUserId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());

        if (!deleteIds.isEmpty()) {
            // 租户成员移除时逻辑删除成员关系，随后同步清理该租户下的用户角色关系。
            LambdaQueryWrapper<UserTenant> deleteWrapper = new LambdaQueryWrapper<>();
            deleteWrapper.in(UserTenant::getId, deleteIds)
                    .eq(UserTenant::getDeleteFlag, 0);
            softDeleteUserTenants(deleteWrapper, operator);
        }
        deleteTenantUserRoles(tenantId, deleteUserIds, operator);

        for (String userId : targetUserIdSet) {
            if (existingNormalUserIdSet.contains(userId)) {
                continue;
            }
            upsertUserTenantRelation(userId, tenantId);
        }
    }

    /**
     * 新增、恢复或刷新用户-租户成员关系。
     */
    private void upsertUserTenantRelation(String userId, String tenantId) {
        LambdaQueryWrapper<UserTenant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserTenant::getUserId, userId)
                .eq(UserTenant::getTenantId, tenantId);
        UserTenant existingRelation = userTenantMapper.selectOne(wrapper);
        if (existingRelation != null) {
            // 关系存在但已逻辑删除时恢复，避免重复插入用户-租户关系。
            if (existingRelation.getDeleteFlag() != null && existingRelation.getDeleteFlag() == 1) {
                existingRelation.setDeleteFlag(0);
                existingRelation.setDeleteTime(null);
                existingRelation.setDeleteBy(null);
            }
            if (!TENANT_USER_STATUS_NORMAL.equals(existingRelation.getStatus())) {
                existingRelation.setStatus(TENANT_USER_STATUS_NORMAL);
            }
            userTenantMapper.updateById(existingRelation);
            return;
        }

        UserTenant userTenant = new UserTenant();
        userTenant.setUserId(userId);
        userTenant.setTenantId(tenantId);
        userTenant.setStatus(TENANT_USER_STATUS_NORMAL);
        userTenantMapper.insert(userTenant);
    }

    /**
     * 移出租户成员时，同步逻辑删除这些用户在该租户下的角色关系。
     */
    private void deleteTenantUserRoles(String tenantId, List<String> userIds, String deleteBy) {
        if (userIds.isEmpty()) {
            return;
        }

        runWithTenant(tenantId, () -> {
            LambdaQueryWrapper<UserRole> wrapper = new LambdaQueryWrapper<>();
            wrapper.in(UserRole::getUserId, userIds)
                    .eq(UserRole::getDeleteFlag, 0);
            softDeleteUserRoles(wrapper, deleteBy);
            return null;
        });
    }

    /**
     * 查询租户下未删除角色 ID。
     */
    private List<String> getTenantRoleIds(String tenantId) {
        return runWithTenant(tenantId, () -> {
            LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Role::getTenantId, tenantId)
                    .eq(Role::getDeleteFlag, 0);
            return roleMapper.selectList(wrapper).stream()
                    .map(Role::getId)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .collect(Collectors.toList());
        });
    }

    /**
     * 删除租户时，逻辑删除该租户角色对应的权限关系。
     */
    private void deleteTenantRolePermissions(String tenantId, List<String> roleIds, String deleteBy) {
        if (!roleIds.isEmpty()) {
            LambdaQueryWrapper<RolePermission> rolePermissionWrapper = new LambdaQueryWrapper<>();
            rolePermissionWrapper.in(RolePermission::getRoleId, roleIds)
                    .eq(RolePermission::getDeleteFlag, 0);
            softDeleteRolePermissions(rolePermissionWrapper, deleteBy);
        }

        LambdaQueryWrapper<RolePermission> tenantRolePermissionWrapper = new LambdaQueryWrapper<>();
        tenantRolePermissionWrapper.eq(RolePermission::getTenantId, tenantId)
                .eq(RolePermission::getDeleteFlag, 0);
        softDeleteRolePermissions(tenantRolePermissionWrapper, deleteBy);
    }

    /**
     * 删除租户时，逻辑删除该租户角色对应的用户关系。
     */
    private void deleteTenantUserRoleRelations(String tenantId, List<String> roleIds, String deleteBy) {
        if (!roleIds.isEmpty()) {
            LambdaQueryWrapper<UserRole> userRoleWrapper = new LambdaQueryWrapper<>();
            userRoleWrapper.in(UserRole::getRoleId, roleIds)
                    .eq(UserRole::getDeleteFlag, 0);
            softDeleteUserRoles(userRoleWrapper, deleteBy);
        }

        LambdaQueryWrapper<UserRole> tenantUserRoleWrapper = new LambdaQueryWrapper<>();
        tenantUserRoleWrapper.eq(UserRole::getTenantId, tenantId)
                .eq(UserRole::getDeleteFlag, 0);
        softDeleteUserRoles(tenantUserRoleWrapper, deleteBy);
    }

    /**
     * 按条件逻辑删除角色-权限关系。
     */
    private void softDeleteRolePermissions(LambdaQueryWrapper<RolePermission> wrapper, String deleteBy) {
        RolePermission rolePermission = new RolePermission();
        EntityFillUtils.fillDeleteFields(rolePermission, deleteBy);
        rolePermissionMapper.update(rolePermission, wrapper);
    }

    /**
     * 按条件逻辑删除用户-角色关系。
     */
    private void softDeleteUserRoles(LambdaQueryWrapper<UserRole> wrapper, String deleteBy) {
        UserRole userRole = new UserRole();
        EntityFillUtils.fillDeleteFields(userRole, deleteBy);
        userRoleMapper.update(userRole, wrapper);
    }

    /**
     * 按条件逻辑删除用户-租户关系。
     */
    private void softDeleteUserTenants(LambdaQueryWrapper<UserTenant> wrapper, String deleteBy) {
        UserTenant userTenant = new UserTenant();
        EntityFillUtils.fillDeleteFields(userTenant, deleteBy);
        userTenantMapper.update(userTenant, wrapper);
    }

    /**
     * 校验租户 ID 不为空。
     */
    private void validateTenantId(String tenantId) {
        getExistingTenant(tenantId);
    }

    /**
     * 查询未删除租户，不存在时抛出业务异常。
     */
    private Tenant getExistingTenant(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            throw new IllegalArgumentException("租户ID不能为空");
        }

        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null || (tenant.getDeleteFlag() != null && tenant.getDeleteFlag() == 1)) {
            throw new IllegalArgumentException("租户不存在或已删除");
        }
        return tenant;
    }

    /**
     * 按约定生成租户默认管理员角色编码。
     */
    private String buildTenantAdminRoleCode(String tenantId) {
        return TENANT_ADMIN_ROLE_CODE_PREFIX + tenantId.trim();
    }

    /**
     * 按约定生成租户默认管理员角色名称。
     */
    private String buildTenantAdminRoleName(Tenant tenant) {
        if (tenant != null && StringUtils.hasText(tenant.getName())) {
            return tenant.getName().trim() + TENANT_ADMIN_ROLE_NAME_SUFFIX;
        }
        return "租户管理员";
    }

    /**
     * 解析操作人账号，缺省时使用 system 作为兜底。
     */
    private String resolveOperator(String operator) {
        if (StringUtils.hasText(operator)) {
            return operator;
        }
        return "system";
    }

    /**
     * 临时切换租户上下文执行查询或写入，并在结束后恢复原上下文。
     */
    private <T> T runWithTenant(String tenantId, Supplier<T> supplier) {
        String previousTenantId = TenantContextHolder.getCurrentTenantId();
        try {
            TenantContextHolder.setCurrentTenantId(tenantId);
            return supplier.get();
        } finally {
            if (StringUtils.hasText(previousTenantId)) {
                TenantContextHolder.setCurrentTenantId(previousTenantId);
            } else {
                TenantContextHolder.clear();
            }
        }
    }
}
