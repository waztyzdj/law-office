package com.lawoffice.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.framework.entity.BaseEntity;
import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.system.constant.DepartRoleCodes;
import com.lawoffice.system.entity.DepartRole;
import com.lawoffice.system.entity.DepartRolePermission;
import com.lawoffice.system.entity.DepartRoleUser;
import com.lawoffice.system.entity.Permission;
import com.lawoffice.system.entity.SysDepart;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.mapper.DepartRoleMapper;
import com.lawoffice.system.mapper.DepartRolePermissionMapper;
import com.lawoffice.system.mapper.DepartRoleUserMapper;
import com.lawoffice.system.mapper.PermissionMapper;
import com.lawoffice.system.mapper.SysDepartMapper;
import com.lawoffice.system.mapper.UserMapper;
import com.lawoffice.system.service.IDepartRoleService;
import com.lawoffice.system.vo.DepartRoleVO;
import com.lawoffice.util.EntityFillUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DepartRoleServiceImpl extends BaseServiceImpl<DepartRoleMapper, DepartRole, DepartRoleVO> implements IDepartRoleService {

    private static final String DEFAULT_ROLE_DESCRIPTION = "部门默认角色";
    private static final String ADMIN_ROLE_CODE_PREFIX = "ADMIN";

    @Autowired
    private DepartRolePermissionMapper departRolePermissionMapper;

    @Autowired
    private DepartRoleUserMapper departRoleUserMapper;

    @Autowired
    private PermissionMapper permissionMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private SysDepartMapper sysDepartMapper;

    @Override
    protected void doBeforeSave(BaseDTO<DepartRole> saveDTO) {
        DepartRole role = saveDTO.getEntity();
        if (role == null) {
            return;
        }

        role.setDepartId(trimToNull(role.getDepartId()));
        role.setRoleCode(trimToNull(role.getRoleCode()));
        role.setRoleName(trimToNull(role.getRoleName()));
        role.setDescription(trimToNull(role.getDescription()));
        role.setWorkflowEnabled(Objects.equals(role.getWorkflowEnabled(), 1) ? 1 : 0);

        if (!StringUtils.hasText(role.getDepartId())) {
            throw new IllegalArgumentException("部门ID不能为空");
        }
        if (!StringUtils.hasText(role.getRoleCode())) {
            throw new IllegalArgumentException("部门角色编码不能为空");
        }
        if (!StringUtils.hasText(role.getRoleName())) {
            throw new IllegalArgumentException("部门角色名称不能为空");
        }
        boolean isCreate = !StringUtils.hasText(role.getId());
        if (isCreate && hasProtectedRoleCodePrefix(role.getRoleCode())) {
            throw new IllegalArgumentException("自定义部门角色编码不能以 DEPART 或 ADMIN 开头");
        }

        SysDepart depart = getActiveDepart(role.getDepartId());
        if (depart == null) {
            throw new IllegalArgumentException("部门不存在或已被删除");
        }

        if (StringUtils.hasText(role.getId())) {
            DepartRole oldRole = getActiveRole(role.getId());
            if (oldRole == null) {
                throw new IllegalArgumentException("部门角色不存在或已被删除");
            }
            if (isDefaultDepartRole(oldRole, depart)) {
                throw new IllegalArgumentException("部门默认角色不能修改");
            }
            role.setDepartId(oldRole.getDepartId());
            role.setRoleCode(oldRole.getRoleCode());
            if (hasProtectedRoleCodePrefix(role.getRoleCode())) {
                throw new IllegalArgumentException("自定义部门角色编码不能以 DEPART 或 ADMIN 开头");
            }
        } else if (isDefaultRoleCode(role, depart) || DEFAULT_ROLE_DESCRIPTION.equals(role.getDescription())) {
            throw new IllegalArgumentException("部门默认角色由系统自动维护");
        }

        validateUniqueRoleCode(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(String departRoleId, List<String> permissionIds) {
        validateActiveRole(departRoleId);

        // 先删除部门角色现有的所有权限
        LambdaUpdateWrapper<DepartRolePermission> deleteWrapper = new LambdaUpdateWrapper<>();
        deleteWrapper.eq(DepartRolePermission::getRoleId, departRoleId)
                .eq(DepartRolePermission::getDeleteFlag, 0);
        logicDeleteByWrapper(departRolePermissionMapper, new DepartRolePermission(), deleteWrapper, resolveDeleteBy(null));

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
        validateActiveRole(departRoleId);

        // 查询部门角色的权限ID列表
        List<String> permissionIds = getDepartRolePermissionIds(departRoleId);
        if (permissionIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 根据权限ID查询权限详情
        LambdaQueryWrapper<Permission> permissionWrapper = new LambdaQueryWrapper<>();
        permissionWrapper.in(Permission::getId, permissionIds)
                .eq(Permission::getDeleteFlag, 0);
        return permissionMapper.selectList(permissionWrapper);
    }

    @Override
    public List<String> getDepartRolePermissionIds(String departRoleId) {
        validateActiveRole(departRoleId);

        LambdaQueryWrapper<DepartRolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DepartRolePermission::getRoleId, departRoleId)
                .eq(DepartRolePermission::getDeleteFlag, 0);
        return departRolePermissionMapper.selectList(wrapper).stream()
                .map(DepartRolePermission::getPermissionId)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removePermissions(String departRoleId, List<String> permissionIds) {
        validateActiveRole(departRoleId);
        if (permissionIds == null || permissionIds.isEmpty()) {
            return;
        }

        LambdaUpdateWrapper<DepartRolePermission> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(DepartRolePermission::getRoleId, departRoleId)
               .in(DepartRolePermission::getPermissionId, permissionIds)
               .eq(DepartRolePermission::getDeleteFlag, 0);
        logicDeleteByWrapper(departRolePermissionMapper, new DepartRolePermission(), wrapper, resolveDeleteBy(null));

        log.info("移除部门角色权限成功，部门角色ID: {}, 移除权限数量: {}", departRoleId, permissionIds.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignUsers(String departRoleId, List<String> userIds) {
        DepartRole role = validateActiveRole(departRoleId);
        if (isDefaultDepartRole(role)) {
            throw new IllegalArgumentException("部门默认角色用户由部门成员自动维护");
        }

        // 先删除部门角色现有的所有用户
        LambdaUpdateWrapper<DepartRoleUser> deleteWrapper = new LambdaUpdateWrapper<>();
        deleteWrapper.eq(DepartRoleUser::getDroleId, departRoleId)
                .eq(DepartRoleUser::getDeleteFlag, 0);
        logicDeleteByWrapper(departRoleUserMapper, new DepartRoleUser(), deleteWrapper, resolveDeleteBy(null));

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
        validateActiveRole(departRoleId);

        // 查询部门角色的用户ID列表
        List<String> userIds = getDepartRoleUserIds(departRoleId);
        if (userIds.isEmpty()) {
            return new ArrayList<>();
        }

        // 根据用户ID查询用户详情
        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.in(User::getId, userIds)
                .eq(User::getDeleteFlag, 0);
        return userMapper.selectList(userWrapper);
    }

    @Override
    public List<String> getDepartRoleUserIds(String departRoleId) {
        validateActiveRole(departRoleId);

        LambdaQueryWrapper<DepartRoleUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DepartRoleUser::getDroleId, departRoleId)
                .eq(DepartRoleUser::getDeleteFlag, 0);
        return departRoleUserMapper.selectList(wrapper).stream()
                .map(DepartRoleUser::getUserId)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeUsers(String departRoleId, List<String> userIds) {
        DepartRole role = validateActiveRole(departRoleId);
        if (isDefaultDepartRole(role)) {
            throw new IllegalArgumentException("部门默认角色用户由部门成员自动维护");
        }
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        LambdaUpdateWrapper<DepartRoleUser> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(DepartRoleUser::getDroleId, departRoleId)
               .in(DepartRoleUser::getUserId, userIds)
               .eq(DepartRoleUser::getDeleteFlag, 0);
        logicDeleteByWrapper(departRoleUserMapper, new DepartRoleUser(), wrapper, resolveDeleteBy(null));

        log.info("移除部门角色用户成功，部门角色ID: {}, 移除用户数量: {}", departRoleId, userIds.size());
    }

    @Override
    protected void doBeforeDelete(BaseDTO<DepartRole> deleteDTO) {
        List<String> roleIds = getDeleteIds(deleteDTO);
        if (roleIds.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<DepartRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(DepartRole::getId, roleIds)
                .eq(DepartRole::getDeleteFlag, 0);
        List<String> protectedNames = baseMapper.selectList(wrapper).stream()
                .filter(this::isDefaultDepartRole)
                .map(DepartRole::getRoleName)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (!protectedNames.isEmpty()) {
            throw new IllegalArgumentException("部门默认角色不能删除: " + String.join(",", protectedNames));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    protected void doAfterDelete(BaseDTO<DepartRole> deleteDTO) {
        List<String> roleIds = getDeleteIds(deleteDTO);
        if (roleIds.isEmpty()) {
            return;
        }

        String deleteBy = resolveDeleteBy(deleteDTO);

        LambdaUpdateWrapper<DepartRolePermission> rolePermissionWrapper = new LambdaUpdateWrapper<>();
        rolePermissionWrapper.in(DepartRolePermission::getRoleId, roleIds)
                .eq(DepartRolePermission::getDeleteFlag, 0);
        logicDeleteByWrapper(departRolePermissionMapper, new DepartRolePermission(), rolePermissionWrapper, deleteBy);

        LambdaUpdateWrapper<DepartRoleUser> roleUserWrapper = new LambdaUpdateWrapper<>();
        roleUserWrapper.in(DepartRoleUser::getDroleId, roleIds)
                .eq(DepartRoleUser::getDeleteFlag, 0);
        logicDeleteByWrapper(departRoleUserMapper, new DepartRoleUser(), roleUserWrapper, deleteBy);
    }

    /**
     * 校验部门角色编码在当前部门内唯一。
     */
    private void validateUniqueRoleCode(DepartRole role) {
        LambdaQueryWrapper<DepartRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DepartRole::getDepartId, role.getDepartId())
                .eq(DepartRole::getRoleCode, role.getRoleCode())
                .eq(DepartRole::getDeleteFlag, 0);
        if (StringUtils.hasText(role.getTenantId())) {
            wrapper.eq(DepartRole::getTenantId, role.getTenantId());
        }
        if (StringUtils.hasText(role.getId())) {
            wrapper.ne(DepartRole::getId, role.getId());
        }
        if (baseMapper.selectCount(wrapper) > 0) {
            throw new IllegalArgumentException("部门角色编码已存在");
        }
    }

    /**
     * 查询并校验部门角色存在且未删除。
     */
    private DepartRole validateActiveRole(String departRoleId) {
        if (!StringUtils.hasText(departRoleId)) {
            throw new IllegalArgumentException("部门角色ID不能为空");
        }
        DepartRole role = getActiveRole(departRoleId);
        if (role == null) {
            throw new IllegalArgumentException("部门角色不存在或已被删除");
        }
        return role;
    }

    private DepartRole getActiveRole(String departRoleId) {
        if (!StringUtils.hasText(departRoleId)) {
            return null;
        }
        DepartRole role = baseMapper.selectById(departRoleId);
        if (role == null || (role.getDeleteFlag() != null && role.getDeleteFlag() == 1)) {
            return null;
        }
        return role;
    }

    private SysDepart getActiveDepart(String departId) {
        if (!StringUtils.hasText(departId)) {
            return null;
        }
        SysDepart depart = sysDepartMapper.selectById(departId);
        if (depart == null || (depart.getDeleteFlag() != null && depart.getDeleteFlag() == 1)) {
            return null;
        }
        return depart;
    }

    /**
     * 部门默认角色由系统维护，仅允许在权限授权环节变更其权限。
     */
    private boolean isDefaultDepartRole(DepartRole role) {
        if (role == null) {
            return false;
        }
        SysDepart depart = getActiveDepart(role.getDepartId());
        return isDefaultDepartRole(role, depart);
    }

    private boolean isDefaultDepartRole(DepartRole role, SysDepart depart) {
        return role != null
                && (DEFAULT_ROLE_DESCRIPTION.equals(role.getDescription()) || isDefaultRoleCode(role, depart));
    }

    private boolean isDefaultRoleCode(DepartRole role, SysDepart depart) {
        if (role == null || depart == null || !StringUtils.hasText(role.getRoleCode())) {
            return false;
        }
        String expectedRoleCode = DepartRoleCodes.buildDefaultRoleCode(depart.getTenantId(), depart.getOrgCode());
        return role.getRoleCode().equals(expectedRoleCode) || role.getRoleCode().equals(depart.getId());
    }

    private boolean hasProtectedRoleCodePrefix(String roleCode) {
        return StringUtils.hasText(roleCode)
                && (roleCode.startsWith(DepartRoleCodes.DEFAULT_DEPART_ROLE_PREFIX)
                || roleCode.startsWith(ADMIN_ROLE_CODE_PREFIX));
    }

    /**
     * 从删除请求中解析单个或批量删除 ID。
     */
    private List<String> getDeleteIds(BaseDTO<DepartRole> deleteDTO) {
        List<String> ids = new ArrayList<>();
        if (deleteDTO == null) {
            return ids;
        }
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

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    /**
     * 对部门角色关联关系执行逻辑删除，保留授权和成员关系审计痕迹。
     */
    private <T extends BaseEntity> void logicDeleteByWrapper(
            BaseMapper<T> mapper,
            T entity,
            LambdaUpdateWrapper<T> wrapper,
            String deleteBy) {
        EntityFillUtils.fillDeleteFields(entity, deleteBy);
        mapper.update(entity, wrapper);
    }

    private String resolveDeleteBy(BaseDTO<?> dto) {
        if (dto != null && dto.getContext() != null && StringUtils.hasText(dto.getContext().getUsername())) {
            return dto.getContext().getUsername();
        }
        return "system";
    }
}
