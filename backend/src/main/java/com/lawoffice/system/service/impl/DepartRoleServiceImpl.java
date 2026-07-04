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
import com.lawoffice.system.entity.UserDepart;
import com.lawoffice.system.mapper.DepartRoleMapper;
import com.lawoffice.system.mapper.DepartRolePermissionMapper;
import com.lawoffice.system.mapper.DepartRoleUserMapper;
import com.lawoffice.system.mapper.PermissionMapper;
import com.lawoffice.system.mapper.SysDepartMapper;
import com.lawoffice.system.mapper.UserDepartMapper;
import com.lawoffice.system.mapper.UserMapper;
import com.lawoffice.system.service.ITokenService;
import com.lawoffice.system.service.IDepartRoleService;
import com.lawoffice.system.service.IUserService;
import com.lawoffice.system.vo.DepartRoleVO;
import com.lawoffice.util.EntityFillUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private UserDepartMapper userDepartMapper;

    @Autowired
    private SysDepartMapper sysDepartMapper;

    @Autowired
    private ITokenService tokenService;

    @Autowired
    private IUserService userService;

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
        DepartRole role = validateActiveRole(departRoleId);
        List<String> targetPermissionIds = normalizeIds(permissionIds);
        String operator = resolveDeleteBy(null);
        List<String> affectedUserIds = listDepartRoleUserIds(departRoleId);

        List<DepartRolePermission> existingRolePermissions = listActiveRolePermissions(departRoleId);
        Set<String> targetPermissionIdSet = Set.copyOf(targetPermissionIds);
        List<String> stalePermissionIds = existingRolePermissions.stream()
                .map(DepartRolePermission::getPermissionId)
                .filter(StringUtils::hasText)
                .filter(permissionId -> !targetPermissionIdSet.contains(permissionId))
                .distinct()
                .collect(Collectors.toList());
        if (!stalePermissionIds.isEmpty()) {
            softDeleteRolePermissions(departRoleId, stalePermissionIds, operator);
        }

        Set<String> existingPermissionIds = existingRolePermissions.stream()
                .map(DepartRolePermission::getPermissionId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        List<String> missingPermissionIds = targetPermissionIds.stream()
                .filter(permissionId -> !existingPermissionIds.contains(permissionId))
                .collect(Collectors.toList());
        restoreOrInsertRolePermissions(role, missingPermissionIds, operator);

        log.info("为部门角色分配权限成功，部门角色ID: {}, 权限数量: {}", departRoleId, targetPermissionIds.size());
        refreshUsersAuthorizationByUserIds(affectedUserIds);
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
        List<String> targetPermissionIds = normalizeIds(permissionIds);
        if (targetPermissionIds.isEmpty()) {
            return;
        }
        softDeleteRolePermissions(departRoleId, targetPermissionIds, resolveDeleteBy(null));
        log.info("移除部门角色权限成功，部门角色ID: {}, 移除权限数量: {}", departRoleId, targetPermissionIds.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignUsers(String departRoleId, List<String> userIds) {
        DepartRole role = validateActiveRole(departRoleId);
        if (DEFAULT_ROLE_DESCRIPTION.equals(role.getDescription())) {
            throw new IllegalArgumentException("部门默认角色用户由部门成员自动维护");
        }
        SysDepart depart = getActiveDepart(role.getDepartId());
        if (depart == null) {
            throw new IllegalArgumentException("部门不存在或已被删除");
        }
        if (isDefaultDepartRole(role, depart)) {
            throw new IllegalArgumentException("部门默认角色用户由部门成员自动维护");
        }

        List<String> targetUserIds = userIds == null ? List.of() : userIds.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        validateDepartRoleUserScope(depart, targetUserIds);
        String operator = resolveDeleteBy(null);

        LambdaQueryWrapper<DepartRoleUser> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DepartRoleUser::getDroleId, departRoleId)
                .eq(DepartRoleUser::getDeleteFlag, 0);
        List<DepartRoleUser> existingRoleUsers = departRoleUserMapper.selectList(queryWrapper);

        // 只软删本次移除的成员，避免未变化成员软删时撞上历史 delete_flag=1 唯一索引。
        Set<String> targetUserIdSet = Set.copyOf(targetUserIds);
        List<String> staleUserIds = existingRoleUsers.stream()
                .map(DepartRoleUser::getUserId)
                .filter(StringUtils::hasText)
                .filter(userId -> !targetUserIdSet.contains(userId))
                .distinct()
                .collect(Collectors.toList());
        if (!staleUserIds.isEmpty()) {
            softDeleteRoleUsers(departRoleId, staleUserIds, operator);
        }

        Set<String> existingUserIds = existingRoleUsers.stream()
                .map(DepartRoleUser::getUserId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        List<String> missingUserIds = targetUserIds.stream()
                .filter(userId -> !existingUserIds.contains(userId))
                .collect(Collectors.toList());
        restoreOrInsertRoleUsers(role, missingUserIds, operator);

        log.info("为部门角色分配用户成功，部门角色ID: {}, 用户数量: {}", departRoleId, targetUserIds.size());
        Set<String> affectedUserIds = new LinkedHashSet<>(existingUserIds);
        affectedUserIds.addAll(targetUserIds);
        refreshUsersAuthorizationByUserIds(new ArrayList<>(affectedUserIds));
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
        List<String> targetUserIds = normalizeIds(userIds);
        if (targetUserIds.isEmpty()) {
            return;
        }
        softDeleteRoleUsers(departRoleId, targetUserIds, resolveDeleteBy(null));
        log.info("移除部门角色用户成功，部门角色ID: {}, 移除用户数量: {}", departRoleId, targetUserIds.size());
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

        refreshUsersAuthorizationByRoleIds(roleIds);

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
     * 部门角色成员只能从角色所属部门及其下级部门成员中选择，避免跨组织授权。
     */
    private void validateDepartRoleUserScope(SysDepart depart, List<String> userIds) {
        if (userIds.isEmpty()) {
            return;
        }
        Set<String> allowedUserIds = getDepartAndChildUserIds(depart);
        List<String> invalidUserIds = userIds.stream()
                .filter(userId -> !allowedUserIds.contains(userId))
                .collect(Collectors.toList());
        if (!invalidUserIds.isEmpty()) {
            throw new IllegalArgumentException("部门角色成员只能选择本部门及下级部门人员");
        }
    }

    private Set<String> getDepartAndChildUserIds(SysDepart depart) {
        Set<String> departIds = getDepartAndChildIds(depart);
        if (departIds.isEmpty()) {
            return Set.of();
        }

        LambdaQueryWrapper<UserDepart> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDepart::getTenantId, depart.getTenantId())
                .in(UserDepart::getDepId, departIds)
                .eq(UserDepart::getDeleteFlag, 0);
        return userDepartMapper.selectList(wrapper).stream()
                .map(UserDepart::getUserId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
    }

    private List<String> listDepartRoleUserIds(String departRoleId) {
        LambdaQueryWrapper<DepartRoleUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DepartRoleUser::getDroleId, departRoleId)
                .eq(DepartRoleUser::getDeleteFlag, 0);
        return departRoleUserMapper.selectList(wrapper).stream()
                .map(DepartRoleUser::getUserId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 授权关系表的唯一键同时约束 delete_flag，删除前先清理历史软删记录，再软删当前有效记录，
     * 否则同一自然键重复“删-加-删”时会撞上 delete_flag=1 的唯一索引。
     */
    private void softDeleteRolePermissions(String departRoleId, List<String> permissionIds, String deleteBy) {
        if (permissionIds.isEmpty()) {
            return;
        }
        LambdaQueryWrapper<DepartRolePermission> purgeWrapper = new LambdaQueryWrapper<>();
        purgeWrapper.eq(DepartRolePermission::getRoleId, departRoleId)
                .in(DepartRolePermission::getPermissionId, permissionIds)
                .eq(DepartRolePermission::getDeleteFlag, 1);
        departRolePermissionMapper.delete(purgeWrapper);

        LambdaUpdateWrapper<DepartRolePermission> deleteWrapper = new LambdaUpdateWrapper<>();
        deleteWrapper.eq(DepartRolePermission::getRoleId, departRoleId)
                .in(DepartRolePermission::getPermissionId, permissionIds)
                .eq(DepartRolePermission::getDeleteFlag, 0);
        logicDeleteByWrapper(departRolePermissionMapper, new DepartRolePermission(), deleteWrapper, deleteBy);
    }

    /**
     * 优先恢复历史软删的权限关系，确保每个“角色-权限”自然键始终只保留一条记录。
     */
    private void restoreOrInsertRolePermissions(DepartRole role, List<String> permissionIds, String operator) {
        if (permissionIds.isEmpty()) {
            return;
        }
        Map<String, DepartRolePermission> deletedPermissionMap = listDeletedRolePermissions(role.getId(), permissionIds)
                .stream()
                .filter(permission -> StringUtils.hasText(permission.getPermissionId()))
                .collect(Collectors.toMap(
                        DepartRolePermission::getPermissionId,
                        permission -> permission,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        for (String permissionId : permissionIds) {
            DepartRolePermission existingDeleted = deletedPermissionMap.get(permissionId);
            if (existingDeleted != null) {
                existingDeleted.setDepartId(role.getDepartId());
                existingDeleted.setRoleId(role.getId());
                existingDeleted.setPermissionId(permissionId);
                existingDeleted.setTenantId(role.getTenantId());
                existingDeleted.setDeleteFlag(0);
                existingDeleted.setDeleteTime(null);
                existingDeleted.setDeleteBy(null);
                existingDeleted.setUpdateBy(operator);
                existingDeleted.setUpdateTime(java.time.LocalDateTime.now());
                departRolePermissionMapper.updateById(existingDeleted);
                continue;
            }
            DepartRolePermission rolePermission = new DepartRolePermission();
            rolePermission.setDepartId(role.getDepartId());
            rolePermission.setRoleId(role.getId());
            rolePermission.setPermissionId(permissionId);
            rolePermission.setTenantId(role.getTenantId());
            departRolePermissionMapper.insert(rolePermission);
        }
    }

    private List<DepartRolePermission> listActiveRolePermissions(String departRoleId) {
        LambdaQueryWrapper<DepartRolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DepartRolePermission::getRoleId, departRoleId)
                .eq(DepartRolePermission::getDeleteFlag, 0);
        return departRolePermissionMapper.selectList(wrapper);
    }

    private List<DepartRolePermission> listDeletedRolePermissions(String departRoleId, List<String> permissionIds) {
        if (permissionIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<DepartRolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DepartRolePermission::getRoleId, departRoleId)
                .in(DepartRolePermission::getPermissionId, permissionIds)
                .eq(DepartRolePermission::getDeleteFlag, 1);
        return departRolePermissionMapper.selectList(wrapper);
    }

    /**
     * 部门角色成员关系与权限关系使用相同的唯一键策略，删除前也要先清理旧的软删记录。
     */
    private void softDeleteRoleUsers(String departRoleId, List<String> userIds, String deleteBy) {
        if (userIds.isEmpty()) {
            return;
        }
        LambdaQueryWrapper<DepartRoleUser> purgeWrapper = new LambdaQueryWrapper<>();
        purgeWrapper.eq(DepartRoleUser::getDroleId, departRoleId)
                .in(DepartRoleUser::getUserId, userIds)
                .eq(DepartRoleUser::getDeleteFlag, 1);
        departRoleUserMapper.delete(purgeWrapper);

        LambdaUpdateWrapper<DepartRoleUser> deleteWrapper = new LambdaUpdateWrapper<>();
        deleteWrapper.eq(DepartRoleUser::getDroleId, departRoleId)
                .in(DepartRoleUser::getUserId, userIds)
                .eq(DepartRoleUser::getDeleteFlag, 0);
        logicDeleteByWrapper(departRoleUserMapper, new DepartRoleUser(), deleteWrapper, deleteBy);
    }

    /**
     * 用户关系恢复后仍沿用原记录，可避免相同“角色-用户”被重复插入出历史脏数据。
     */
    private void restoreOrInsertRoleUsers(DepartRole role, List<String> userIds, String operator) {
        if (userIds.isEmpty()) {
            return;
        }
        Map<String, DepartRoleUser> deletedUserMap = listDeletedRoleUsers(role.getId(), userIds)
                .stream()
                .filter(roleUser -> StringUtils.hasText(roleUser.getUserId()))
                .collect(Collectors.toMap(
                        DepartRoleUser::getUserId,
                        roleUser -> roleUser,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        for (String userId : userIds) {
            DepartRoleUser existingDeleted = deletedUserMap.get(userId);
            if (existingDeleted != null) {
                existingDeleted.setDroleId(role.getId());
                existingDeleted.setUserId(userId);
                existingDeleted.setTenantId(role.getTenantId());
                existingDeleted.setDeleteFlag(0);
                existingDeleted.setDeleteTime(null);
                existingDeleted.setDeleteBy(null);
                existingDeleted.setUpdateBy(operator);
                existingDeleted.setUpdateTime(java.time.LocalDateTime.now());
                departRoleUserMapper.updateById(existingDeleted);
                continue;
            }
            DepartRoleUser roleUser = new DepartRoleUser();
            roleUser.setDroleId(role.getId());
            roleUser.setUserId(userId);
            roleUser.setTenantId(role.getTenantId());
            departRoleUserMapper.insert(roleUser);
        }
    }

    private List<DepartRoleUser> listDeletedRoleUsers(String departRoleId, List<String> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<DepartRoleUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DepartRoleUser::getDroleId, departRoleId)
                .in(DepartRoleUser::getUserId, userIds)
                .eq(DepartRoleUser::getDeleteFlag, 1);
        return departRoleUserMapper.selectList(wrapper);
    }

    private List<String> normalizeIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 部门角色权限变化后，需要同步刷新在线用户的 Redis 权限缓存，
     * 否则 @RequiresPermission 仍会按旧权限拦截。
     */
    private void refreshUsersAuthorizationByRoleIds(List<String> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }
        LambdaQueryWrapper<DepartRoleUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(DepartRoleUser::getDroleId, roleIds)
                .eq(DepartRoleUser::getDeleteFlag, 0);
        List<String> userIds = departRoleUserMapper.selectList(wrapper).stream()
                .map(DepartRoleUser::getUserId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        refreshUsersAuthorizationByUserIds(userIds);
    }

    private void refreshUsersAuthorizationByUserIds(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.in(User::getId, userIds)
                .eq(User::getDeleteFlag, 0);
        userMapper.selectList(userWrapper).stream()
                .map(User::getUsername)
                .filter(StringUtils::hasText)
                .distinct()
                .forEach(username -> {
                    var userInfo = userService.getCurrentUserDetailInfo(username);
                    tokenService.refreshUserAuthorization(username, userInfo.getPermissions(), userInfo.getRoles());
                });
    }

    private Set<String> getDepartAndChildIds(SysDepart rootDepart) {
        Set<String> scopeIds = new LinkedHashSet<>();
        scopeIds.add(rootDepart.getId());

        LambdaQueryWrapper<SysDepart> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDepart::getTenantId, rootDepart.getTenantId())
                .eq(SysDepart::getDeleteFlag, 0);
        List<SysDepart> departs = sysDepartMapper.selectList(wrapper);

        boolean changed;
        do {
            changed = false;
            for (SysDepart depart : departs) {
                if (StringUtils.hasText(depart.getId())
                        && scopeIds.contains(depart.getParentId())
                        && scopeIds.add(depart.getId())) {
                    changed = true;
                }
            }
        } while (changed);

        return scopeIds;
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
