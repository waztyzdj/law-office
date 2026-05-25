package com.lawoffice.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawoffice.framework.config.TenantContextHolder;
import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.system.entity.Permission;
import com.lawoffice.system.entity.Role;
import com.lawoffice.system.entity.RolePermission;
import com.lawoffice.system.entity.Tenant;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.mapper.PermissionMapper;
import com.lawoffice.system.mapper.RolePermissionMapper;
import com.lawoffice.system.mapper.TenantMapper;
import com.lawoffice.system.mapper.UserMapper;
import com.lawoffice.system.service.ITenantLifecycleService;
import com.lawoffice.system.service.ITenantService;
import com.lawoffice.system.service.IUserService;
import com.lawoffice.system.vo.TenantVO;
import com.lawoffice.util.EntityFillUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TenantServiceImpl extends BaseServiceImpl<TenantMapper, Tenant, TenantVO> implements ITenantService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PermissionMapper permissionMapper;

    @Autowired
    private RolePermissionMapper rolePermissionMapper;

    @Autowired
    private IUserService userService;

    @Autowired
    private ITenantLifecycleService tenantLifecycleService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<TenantVO> save(BaseDTO<Tenant> saveDTO) {
        try {
            doBeforeSave(saveDTO);

            Tenant requestData = saveDTO.getEntity();
            RequestContext context = saveDTO.getContext();
            Tenant entity = BeanUtil.copyProperties(requestData, Tenant.class);

            boolean isCreate = baseMapper.selectById(entity.getId()) == null;
            EntityFillUtils.fillAuditFields(entity, context, isCreate);
            this.saveOrUpdate(entity);

            tenantLifecycleService.initializeTenant(
                    entity,
                    resolveAdminUserIds(saveDTO),
                    resolveOperator(saveDTO),
                    resolveOperateTime(entity));

            return BaseResult.success(BeanUtil.toBean(entity, TenantVO.class));
        } catch (IllegalArgumentException e) {
            markRollbackOnly();
            log.warn("保存参数校验失败: {}", e.getMessage());
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            markRollbackOnly();
            log.error("保存失败", e);
            return BaseResult.error("保存失败: " + e.getMessage());
        }
    }

    @Override
    protected void doBeforeSave(BaseDTO<Tenant> saveDTO) {
        Tenant tenant = saveDTO.getEntity();
        if (tenant == null) {
            return;
        }

        tenant.setId(trimToNull(tenant.getId()));
        tenant.setOriginalId(trimToNull(tenant.getOriginalId()));
        if (!StringUtils.hasText(tenant.getId())) {
            throw new IllegalArgumentException("租户编码不能为空");
        }

        if (StringUtils.hasText(tenant.getOriginalId()) && !tenant.getOriginalId().equals(tenant.getId())) {
            throw new IllegalArgumentException("租户编码新增后不能修改");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<Void> delete(BaseDTO<Tenant> deleteDTO) {
        try {
            doBeforeDelete(deleteDTO);

            String id = deleteDTO.getId();
            if (!StringUtils.hasText(id)) {
                return BaseResult.error("ID不能为空");
            }

            getExistingTenant(id);
            String deleteBy = resolveDeleteBy(deleteDTO);
            tenantLifecycleService.deleteTenantRelations(id, deleteBy);
            softDeleteTenant(id, deleteBy);
            return BaseResult.success();
        } catch (Exception e) {
            markRollbackOnly();
            log.error("删除失败", e);
            return BaseResult.error("删除失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<Void> batchDelete(BaseDTO<Tenant> deleteDTO) {
        try {
            doBeforeDelete(deleteDTO);

            List<String> tenantIds = getDeleteTenantIds(deleteDTO);
            if (tenantIds.isEmpty()) {
                return BaseResult.error("删除ID列表不能为空");
            }

            String deleteBy = resolveDeleteBy(deleteDTO);
            for (String tenantId : tenantIds) {
                getExistingTenant(tenantId);
            }
            for (String tenantId : tenantIds) {
                tenantLifecycleService.deleteTenantRelations(tenantId, deleteBy);
                softDeleteTenant(tenantId, deleteBy);
            }
            return BaseResult.success();
        } catch (Exception e) {
            markRollbackOnly();
            log.error("批量删除失败", e);
            return BaseResult.error("批量删除失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignTenantUsers(String tenantId, List<String> userIds) {
        validateTenantId(tenantId);

        List<String> normalizedUserIds = normalizeIds(userIds);
        validateUsers(normalizedUserIds);
        tenantLifecycleService.assignTenantUsers(tenantId, normalizedUserIds, "system");

        log.info("为租户分配用户成功，租户ID: {}, 用户数量: {}", tenantId, normalizedUserIds.size());
    }

    @Override
    public List<String> getTenantUserIds(String tenantId) {
        return tenantLifecycleService.getTenantUserIds(tenantId);
    }

    @Override
    public List<String> getTenantAdminUserIds(String tenantId) {
        return tenantLifecycleService.getTenantAdminUserIds(tenantId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignTenantAdmins(String tenantId, List<String> userIds) {
        validateTenantId(tenantId);

        List<String> normalizedUserIds = normalizeIds(userIds);
        validateUsers(normalizedUserIds);
        tenantLifecycleService.assignTenantAdmins(tenantId, normalizedUserIds, "system");

        log.info("为租户分配管理员成功，租户ID: {}, 用户数量: {}", tenantId, normalizedUserIds.size());
    }

    @Override
    public List<String> getTenantAdminPermissionIds(String tenantId) {
        validateTenantId(tenantId);

        Role adminRole = tenantLifecycleService.ensureTenantAdminRole(tenantId, "system", LocalDateTime.now());
        return runWithTenant(tenantId, () -> {
            LambdaQueryWrapper<RolePermission> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(RolePermission::getRoleId, adminRole.getId())
                    .eq(RolePermission::getDeleteFlag, 0);
            return rolePermissionMapper.selectList(wrapper).stream()
                    .map(RolePermission::getPermissionId)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .collect(Collectors.toList());
        });
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignTenantAdminPermissions(String tenantId, List<String> permissionIds, String operatorUsername) {
        validateTenantId(tenantId);

        Role adminRole = tenantLifecycleService.ensureTenantAdminRole(tenantId, resolveOperator(operatorUsername), LocalDateTime.now());
        List<String> normalizedPermissionIds = expandPermissionIdsWithAncestors(normalizeIds(permissionIds));
        validatePermissions(normalizedPermissionIds);
        validateGrantWithinOperatorPermissions(normalizedPermissionIds, operatorUsername);
        syncTenantAdminRolePermissions(tenantId, adminRole.getId(), normalizedPermissionIds, resolveOperator(operatorUsername));

        log.info("Tenant admin permissions assigned, tenantId: {}, permissionCount: {}", tenantId, normalizedPermissionIds.size());
    }

    private boolean isGrantableToTenantAdmin(String perms) {
        return !"tenant:view".equals(perms)
                && !"tenant:edit".equals(perms)
                && !"permission:view".equals(perms)
                && !"permission:edit".equals(perms)
                && !"log:view".equals(perms)
                && !"log:edit".equals(perms);
    }

    private void validateGrantWithinOperatorPermissions(List<String> permissionIds, String operatorUsername) {
        if (permissionIds.isEmpty() || !StringUtils.hasText(operatorUsername)) {
            return;
        }

        Set<String> operatorPerms = userService.getUserPermissionCodesByUsername(operatorUsername).stream()
                .filter(StringUtils::hasText)
                .filter(this::isGrantableToTenantAdmin)
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
            throw new IllegalArgumentException("Cannot grant permissions beyond current user scope: " + String.join(",", overLimitPerms));
        }
    }

    private void syncTenantAdminRolePermissions(String tenantId, String roleId, List<String> permissionIds, String deleteBy) {
        runWithTenant(tenantId, () -> {
            LambdaQueryWrapper<RolePermission> deleteWrapper = new LambdaQueryWrapper<>();
            deleteWrapper.eq(RolePermission::getRoleId, roleId)
                    .eq(RolePermission::getDeleteFlag, 0);
            softDeleteRolePermissions(deleteWrapper, deleteBy);

            for (String permissionId : permissionIds) {
                RolePermission rolePermission = new RolePermission();
                rolePermission.setRoleId(roleId);
                rolePermission.setPermissionId(permissionId);
                rolePermissionMapper.insert(rolePermission);
            }
            return null;
        });
    }

    private List<String> expandPermissionIdsWithAncestors(List<String> permissionIds) {
        Set<String> expandedIds = new LinkedHashSet<>(permissionIds);
        for (String permissionId : permissionIds) {
            Permission permission = permissionMapper.selectById(permissionId);
            if (permission == null || (permission.getDeleteFlag() != null && permission.getDeleteFlag() == 1)) {
                throw new IllegalArgumentException("Permission does not exist or has been deleted");
            }

            String parentId = permission.getParentId();
            Set<String> visitedParentIds = new LinkedHashSet<>();
            while (StringUtils.hasText(parentId)) {
                if (!visitedParentIds.add(parentId)) {
                    throw new IllegalArgumentException("Permission parent relationship has a cycle");
                }
                Permission parent = permissionMapper.selectById(parentId);
                if (parent == null || (parent.getDeleteFlag() != null && parent.getDeleteFlag() == 1)) {
                    throw new IllegalArgumentException("Parent permission does not exist or has been deleted");
                }
                expandedIds.add(parent.getId());
                parentId = parent.getParentId();
            }
        }
        return new ArrayList<>(expandedIds);
    }

    private void validatePermissions(List<String> permissionIds) {
        if (permissionIds.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<Permission> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Permission::getId, permissionIds)
                .eq(Permission::getDeleteFlag, 0)
                .eq(Permission::getStatus, "1");
        long count = permissionMapper.selectCount(wrapper);
        if (count != permissionIds.size()) {
            throw new IllegalArgumentException("Permission does not exist, has been deleted, or is disabled");
        }
    }

    private void softDeleteRolePermissions(LambdaQueryWrapper<RolePermission> wrapper, String deleteBy) {
        RolePermission rolePermission = new RolePermission();
        EntityFillUtils.fillDeleteFields(rolePermission, deleteBy);
        rolePermissionMapper.update(rolePermission, wrapper);
    }

    private void softDeleteTenant(String tenantId, String deleteBy) {
        Tenant tenant = new Tenant();
        tenant.setId(tenantId);
        EntityFillUtils.fillDeleteFields(tenant, deleteBy);
        baseMapper.updateById(tenant);
    }

    private void validateTenantId(String tenantId) {
        getExistingTenant(tenantId);
    }

    private Tenant getExistingTenant(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            throw new IllegalArgumentException("租户ID不能为空");
        }

        Tenant tenant = baseMapper.selectById(tenantId);
        if (tenant == null || (tenant.getDeleteFlag() != null && tenant.getDeleteFlag() == 1)) {
            throw new IllegalArgumentException("租户不存在或已删除");
        }
        return tenant;
    }

    private void validateUsers(List<String> userIds) {
        if (userIds.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(User::getId, userIds)
                .eq(User::getDeleteFlag, 0)
                .eq(User::getStatus, 1);
        long count = userMapper.selectCount(wrapper);
        if (count != userIds.size()) {
            throw new IllegalArgumentException("包含不存在、已删除或已冻结的用户");
        }
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
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

    private List<String> getDeleteTenantIds(BaseDTO<Tenant> deleteDTO) {
        List<String> ids = new ArrayList<>();
        if (deleteDTO == null) {
            return ids;
        }
        if (StringUtils.hasText(deleteDTO.getId())) {
            ids.add(deleteDTO.getId());
        }
        if (deleteDTO.getDeleteIds() != null) {
            ids.addAll(deleteDTO.getDeleteIds());
        }
        return normalizeIds(ids);
    }

    private String resolveDeleteBy(BaseDTO<Tenant> deleteDTO) {
        if (deleteDTO != null && deleteDTO.getContext() != null
                && StringUtils.hasText(deleteDTO.getContext().getUsername())) {
            return deleteDTO.getContext().getUsername();
        }
        return "system";
    }

    private String resolveOperator(BaseDTO<Tenant> saveDTO) {
        if (saveDTO != null && saveDTO.getContext() != null && StringUtils.hasText(saveDTO.getContext().getUsername())) {
            return saveDTO.getContext().getUsername();
        }
        return "system";
    }

    private String resolveOperator(String operatorUsername) {
        if (StringUtils.hasText(operatorUsername)) {
            return operatorUsername;
        }
        return "system";
    }

    private List<String> resolveAdminUserIds(BaseDTO<Tenant> saveDTO) {
        if (saveDTO == null || saveDTO.getEntity() == null) {
            return new ArrayList<>();
        }
        return normalizeIds(saveDTO.getEntity().getAdminUserIds());
    }

    private LocalDateTime resolveOperateTime(Tenant entity) {
        if (entity.getUpdateTime() != null) {
            return entity.getUpdateTime();
        }
        if (entity.getCreateTime() != null) {
            return entity.getCreateTime();
        }
        return LocalDateTime.now();
    }

    private void markRollbackOnly() {
        try {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        } catch (Exception ignored) {
            // No transaction is active only in unusual proxy bypass scenarios.
        }
    }

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
