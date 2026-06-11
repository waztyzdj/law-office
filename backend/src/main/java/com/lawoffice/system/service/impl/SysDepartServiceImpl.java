package com.lawoffice.system.service.impl;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.framework.dto.BasePageDTO;
import com.lawoffice.framework.entity.BaseEntity;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.service.impl.TreeServiceImpl;
import com.lawoffice.system.constant.DepartRoleCodes;
import com.lawoffice.system.constant.SysDepartOrgTypes;
import com.lawoffice.system.entity.DepartPermission;
import com.lawoffice.system.entity.DepartRole;
import com.lawoffice.system.entity.DepartRolePermission;
import com.lawoffice.system.entity.DepartRoleUser;
import com.lawoffice.system.entity.Permission;
import com.lawoffice.system.entity.SysDepart;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.entity.UserDepart;
import com.lawoffice.system.mapper.DepartPermissionMapper;
import com.lawoffice.system.mapper.DepartRoleMapper;
import com.lawoffice.system.mapper.DepartRolePermissionMapper;
import com.lawoffice.system.mapper.DepartRoleUserMapper;
import com.lawoffice.system.mapper.PermissionMapper;
import com.lawoffice.system.mapper.SysDepartMapper;
import com.lawoffice.system.mapper.UserMapper;
import com.lawoffice.system.mapper.UserDepartMapper;
import com.lawoffice.system.req.DepartLeaderReq;
import com.lawoffice.system.req.DepartMemberRelationReq;
import com.lawoffice.system.service.ISysDepartService;
import com.lawoffice.system.vo.DepartMemberRelationVO;
import com.lawoffice.system.vo.DepartPermissionSourceVO;
import com.lawoffice.system.vo.SysDepartVO;
import com.lawoffice.system.vo.UserVO;
import com.lawoffice.util.EntityFillUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SysDepartServiceImpl extends TreeServiceImpl<SysDepartMapper, SysDepart, SysDepartVO> implements ISysDepartService {

    private static final String DEFAULT_ROLE_DESCRIPTION = "部门默认角色";
    private static final String ENABLED_STATUS = "1";
    private static final String DISABLED_STATUS = "0";
    private static final int FLAG_NO = 0;
    private static final int FLAG_YES = 1;

    private final DepartRoleMapper departRoleMapper;
    private final DepartPermissionMapper departPermissionMapper;
    private final DepartRolePermissionMapper departRolePermissionMapper;
    private final DepartRoleUserMapper departRoleUserMapper;
    private final PermissionMapper permissionMapper;
    private final UserDepartMapper userDepartMapper;
    private final UserMapper userMapper;

    public SysDepartServiceImpl(
            DepartRoleMapper departRoleMapper,
            DepartPermissionMapper departPermissionMapper,
            DepartRolePermissionMapper departRolePermissionMapper,
            DepartRoleUserMapper departRoleUserMapper,
            PermissionMapper permissionMapper,
            UserDepartMapper userDepartMapper,
            UserMapper userMapper) {
        this.departRoleMapper = departRoleMapper;
        this.departPermissionMapper = departPermissionMapper;
        this.departRolePermissionMapper = departRolePermissionMapper;
        this.departRoleUserMapper = departRoleUserMapper;
        this.permissionMapper = permissionMapper;
        this.userDepartMapper = userDepartMapper;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<SysDepartVO> save(BaseDTO<SysDepart> saveDTO) {
        BaseResult<SysDepartVO> result = super.save(saveDTO);
        markRollbackIfFailed(result);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<List<SysDepartVO>> batchSave(BaseDTO<SysDepart> batchSaveDTO) {
        BaseResult<List<SysDepartVO>> result = super.batchSave(batchSaveDTO);
        markRollbackIfFailed(result);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<Void> delete(BaseDTO<SysDepart> deleteDTO) {
        BaseResult<Void> result = super.delete(deleteDTO);
        markRollbackIfFailed(result);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<Void> batchDelete(BaseDTO<SysDepart> deleteDTO) {
        BaseResult<Void> result = super.batchDelete(deleteDTO);
        markRollbackIfFailed(result);
        return result;
    }

    @Override
    protected void doBeforeList(BaseDTO<SysDepart> baseDTO) {
        applyDepartOrder(resolveQueryWrapper(baseDTO));
    }

    @Override
    protected void doBeforePage(BasePageDTO<SysDepart> basePageDTO) {
        applyDepartOrder(resolveQueryWrapper(basePageDTO));
    }

    @Override
    protected void applyTreeOrder(QueryWrapper<SysDepart> wrapper) {
        applyDepartOrder(wrapper);
    }

    @Override
    protected Comparator<SysDepartVO> treeNodeComparator() {
        return Comparator.comparing(
                SysDepartVO::getDepartOrder,
                Comparator.nullsLast(Integer::compareTo)
        );
    }

    @Override
    protected void doBeforeSave(BaseDTO<SysDepart> saveDTO) {
        SysDepart depart = saveDTO == null ? null : saveDTO.getEntity();
        Assert.notNull(depart, "部门数据不能为空");

        normalizeDepart(depart);
        validateDepart(depart, saveDTO);
    }

    @Override
    protected void doBeforeBatchSave(BaseDTO<SysDepart> batchSaveDTO) {
        List<SysDepart> departs = batchSaveDTO == null ? null : batchSaveDTO.getEntityList();
        Assert.notEmpty(departs, "保存数据不能为空");

        Set<String> orgCodes = new HashSet<>();
        for (SysDepart depart : departs) {
            Assert.notNull(depart, "部门数据不能为空");
            normalizeDepart(depart);
            validateDepart(depart, batchSaveDTO);
            if (!orgCodes.add(depart.getOrgCode())) {
                throw new IllegalArgumentException("机构编码重复: " + depart.getOrgCode());
            }
        }
    }

    @Override
    protected void doAfterSave(BaseDTO<SysDepart> saveDTO, SysDepartVO vo) {
        if (vo == null || !StringUtils.hasText(vo.getId())) {
            return;
        }

        SysDepart savedDepart = baseMapper.selectById(vo.getId());
        if (savedDepart == null) {
            return;
        }

        ensureDefaultDepartRole(savedDepart, saveDTO);
        refreshLeafFlags();
    }

    @Override
    protected void doAfterBatchSave(BaseDTO<SysDepart> batchSaveDTO, List<SysDepartVO> voList) {
        if (voList == null || voList.isEmpty()) {
            return;
        }

        for (SysDepartVO vo : voList) {
            if (vo == null || !StringUtils.hasText(vo.getId())) {
                continue;
            }
            SysDepart savedDepart = baseMapper.selectById(vo.getId());
            if (savedDepart != null) {
                ensureDefaultDepartRole(savedDepart, batchSaveDTO);
            }
        }
        refreshLeafFlags();
    }

    @Override
    protected void doBeforeDelete(BaseDTO<SysDepart> deleteDTO) {
        List<String> departIds = resolveDepartIds(deleteDTO);
        if (departIds.isEmpty()) {
            return;
        }

        try {
            validateNoChildrenBeforeDelete(deleteDTO);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("存在下级部门，请先删除或调整下级部门");
        }

    }

    @Override
    protected void doAfterDelete(BaseDTO<SysDepart> deleteDTO) {
        List<String> departIds = resolveDepartIds(deleteDTO);
        if (departIds.isEmpty()) {
            return;
        }

        List<String> departRoleIds = getDepartRoleIds(departIds);
        String deleteBy = resolveDeleteBy(deleteDTO);

        LambdaUpdateWrapper<UserDepart> userDepartWrapper = new LambdaUpdateWrapper<>();
        userDepartWrapper.in(UserDepart::getDepId, departIds)
                .eq(UserDepart::getDeleteFlag, 0);
        logicDeleteByWrapper(userDepartMapper, new UserDepart(), userDepartWrapper, deleteBy);

        if (!departRoleIds.isEmpty()) {
            LambdaUpdateWrapper<DepartRoleUser> roleUserWrapper = new LambdaUpdateWrapper<>();
            roleUserWrapper.in(DepartRoleUser::getDroleId, departRoleIds)
                    .eq(DepartRoleUser::getDeleteFlag, 0);
            logicDeleteByWrapper(departRoleUserMapper, new DepartRoleUser(), roleUserWrapper, deleteBy);

            LambdaUpdateWrapper<DepartRolePermission> rolePermissionWrapper = new LambdaUpdateWrapper<>();
            rolePermissionWrapper.in(DepartRolePermission::getRoleId, departRoleIds)
                    .eq(DepartRolePermission::getDeleteFlag, 0);
            logicDeleteByWrapper(departRolePermissionMapper, new DepartRolePermission(), rolePermissionWrapper, deleteBy);
        }

        LambdaUpdateWrapper<DepartPermission> departPermissionWrapper = new LambdaUpdateWrapper<>();
        departPermissionWrapper.in(DepartPermission::getDepartId, departIds)
                .eq(DepartPermission::getDeleteFlag, 0);
        logicDeleteByWrapper(departPermissionMapper, new DepartPermission(), departPermissionWrapper, deleteBy);

        LambdaUpdateWrapper<DepartRole> departRoleWrapper = new LambdaUpdateWrapper<>();
        departRoleWrapper.in(DepartRole::getDepartId, departIds)
                .eq(DepartRole::getDeleteFlag, 0);
        logicDeleteByWrapper(departRoleMapper, new DepartRole(), departRoleWrapper, deleteBy);

        refreshLeafFlags();
        log.info("删除部门关联数据成功，部门数量: {}, 部门角色数量: {}", departIds.size(), departRoleIds.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(String departId, List<String> roleIds) {
        if (!StringUtils.hasText(departId)) {
            return;
        }
        validateDepartExists(departId);

        List<String> normalizedRoleIds = normalizeIds(roleIds);
        if (normalizedRoleIds.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<DepartRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(DepartRole::getId, normalizedRoleIds)
                .eq(DepartRole::getDeleteFlag, 0);

        List<DepartRole> roles = departRoleMapper.selectList(wrapper);
        for (DepartRole role : roles) {
            role.setDepartId(departId);
            departRoleMapper.updateById(role);
        }

        log.info("为部门绑定角色成功，部门ID: {}, 角色数量: {}", departId, roles.size());
    }

    @Override
    public List<DepartRole> getDepartRoles(String departId) {
        if (!StringUtils.hasText(departId)) {
            return new ArrayList<>();
        }

        SysDepart depart = baseMapper.selectById(departId);
        LambdaQueryWrapper<DepartRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DepartRole::getDepartId, departId)
                .eq(DepartRole::getDeleteFlag, 0);
        List<DepartRole> roles = departRoleMapper.selectList(wrapper);
        roles.forEach(role -> role.setDefaultRole(isDefaultDepartRole(role, depart)));
        return roles;
    }

    @Override
    public List<String> getDepartRoleIds(String departId) {
        return getDepartRoles(departId).stream()
                .map(DepartRole::getId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeRoles(String departId, List<String> roleIds) {
        if (!StringUtils.hasText(departId) || roleIds == null || roleIds.isEmpty()) {
            return;
        }

        List<String> normalizedRoleIds = normalizeIds(roleIds);
        if (normalizedRoleIds.isEmpty()) {
            return;
        }

        String deleteBy = resolveDeleteBy(null);
        LambdaUpdateWrapper<DepartRoleUser> roleUserWrapper = new LambdaUpdateWrapper<>();
        roleUserWrapper.in(DepartRoleUser::getDroleId, normalizedRoleIds)
                .eq(DepartRoleUser::getDeleteFlag, 0);
        logicDeleteByWrapper(departRoleUserMapper, new DepartRoleUser(), roleUserWrapper, deleteBy);

        LambdaUpdateWrapper<DepartRolePermission> rolePermissionWrapper = new LambdaUpdateWrapper<>();
        rolePermissionWrapper.in(DepartRolePermission::getRoleId, normalizedRoleIds)
                .eq(DepartRolePermission::getDeleteFlag, 0);
        logicDeleteByWrapper(departRolePermissionMapper, new DepartRolePermission(), rolePermissionWrapper, deleteBy);

        LambdaUpdateWrapper<DepartRole> roleWrapper = new LambdaUpdateWrapper<>();
        roleWrapper.eq(DepartRole::getDepartId, departId)
                .in(DepartRole::getId, normalizedRoleIds)
                .eq(DepartRole::getDeleteFlag, 0);
        logicDeleteByWrapper(departRoleMapper, new DepartRole(), roleWrapper, deleteBy);

        log.info("移除部门角色成功，部门ID: {}, 移除角色数量: {}", departId, normalizedRoleIds.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(String departId, List<String> permissionIds) {
        if (!StringUtils.hasText(departId)) {
            return;
        }
        validateDepartExists(departId);

        LambdaUpdateWrapper<DepartPermission> deleteWrapper = new LambdaUpdateWrapper<>();
        deleteWrapper.eq(DepartPermission::getDepartId, departId)
                .eq(DepartPermission::getDeleteFlag, 0);
        logicDeleteByWrapper(departPermissionMapper, new DepartPermission(), deleteWrapper, resolveDeleteBy(null));

        List<String> normalizedPermissionIds = normalizeIds(permissionIds);
        if (!normalizedPermissionIds.isEmpty()) {
            List<DepartPermission> departPermissions = normalizedPermissionIds.stream()
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

        log.info("为部门分配权限成功，部门ID: {}, 权限数量: {}", departId, normalizedPermissionIds.size());
    }

    @Override
    public List<Permission> getDepartPermissions(String departId) {
        if (!StringUtils.hasText(departId)) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<DepartPermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DepartPermission::getDepartId, departId)
                .eq(DepartPermission::getDeleteFlag, 0);
        List<DepartPermission> departPermissions = departPermissionMapper.selectList(wrapper);

        if (departPermissions.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> permissionIds = departPermissions.stream()
                .map(DepartPermission::getPermissionId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());

        if (permissionIds.isEmpty()) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<Permission> permissionWrapper = new LambdaQueryWrapper<>();
        permissionWrapper.in(Permission::getId, permissionIds)
                .eq(Permission::getDeleteFlag, 0);
        return permissionMapper.selectList(permissionWrapper);
    }

    @Override
    public List<String> getDepartPermissionIds(String departId) {
        if (!StringUtils.hasText(departId)) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<DepartPermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DepartPermission::getDepartId, departId)
                .eq(DepartPermission::getDeleteFlag, 0);
        return departPermissionMapper.selectList(wrapper).stream()
                .map(DepartPermission::getPermissionId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public List<DepartPermissionSourceVO> getDepartPermissionSources(String departId) {
        if (!StringUtils.hasText(departId)) {
            return new ArrayList<>();
        }

        List<DepartPermission> directPermissions = getDirectDepartPermissionRelations(departId);
        List<DepartRole> departRoles = getDepartRoles(departId);
        List<String> departRoleIds = departRoles.stream()
                .map(DepartRole::getId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        List<DepartRolePermission> rolePermissions = getDepartRolePermissionRelations(departRoleIds);

        Set<String> permissionIds = new HashSet<>();
        directPermissions.stream()
                .map(DepartPermission::getPermissionId)
                .filter(StringUtils::hasText)
                .forEach(permissionIds::add);
        rolePermissions.stream()
                .map(DepartRolePermission::getPermissionId)
                .filter(StringUtils::hasText)
                .forEach(permissionIds::add);
        if (permissionIds.isEmpty()) {
            return new ArrayList<>();
        }

        Map<String, Permission> permissionMap = getPermissionMap(permissionIds);
        Map<String, DepartRole> roleMap = departRoles.stream()
                .filter(role -> StringUtils.hasText(role.getId()))
                .collect(Collectors.toMap(DepartRole::getId, role -> role, (left, right) -> left));

        List<DepartPermissionSourceVO> sources = new ArrayList<>();
        for (DepartPermission relation : directPermissions) {
            Permission permission = permissionMap.get(relation.getPermissionId());
            if (permission != null) {
                sources.add(buildPermissionSource(permission, "depart", departId, "部门直接权限"));
            }
        }
        for (DepartRolePermission relation : rolePermissions) {
            Permission permission = permissionMap.get(relation.getPermissionId());
            DepartRole role = roleMap.get(relation.getRoleId());
            if (permission != null && role != null) {
                sources.add(buildPermissionSource(permission, "role", role.getId(), role.getRoleName()));
            }
        }
        return sources;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removePermissions(String departId, List<String> permissionIds) {
        List<String> normalizedPermissionIds = normalizeIds(permissionIds);
        if (!StringUtils.hasText(departId) || normalizedPermissionIds.isEmpty()) {
            return;
        }

        LambdaUpdateWrapper<DepartPermission> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(DepartPermission::getDepartId, departId)
                .in(DepartPermission::getPermissionId, normalizedPermissionIds)
                .eq(DepartPermission::getDeleteFlag, 0);
        logicDeleteByWrapper(departPermissionMapper, new DepartPermission(), wrapper, resolveDeleteBy(null));

        log.info("移除部门权限成功，部门ID: {}, 移除权限数量: {}", departId, normalizedPermissionIds.size());
    }

    private List<DepartPermission> getDirectDepartPermissionRelations(String departId) {
        LambdaQueryWrapper<DepartPermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DepartPermission::getDepartId, departId)
                .eq(DepartPermission::getDeleteFlag, 0);
        return departPermissionMapper.selectList(wrapper);
    }

    private List<DepartRolePermission> getDepartRolePermissionRelations(List<String> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<DepartRolePermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(DepartRolePermission::getRoleId, roleIds)
                .eq(DepartRolePermission::getDeleteFlag, 0);
        return departRolePermissionMapper.selectList(wrapper);
    }

    private Map<String, Permission> getPermissionMap(Set<String> permissionIds) {
        LambdaQueryWrapper<Permission> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Permission::getId, permissionIds)
                .eq(Permission::getDeleteFlag, 0);
        return permissionMapper.selectList(wrapper).stream()
                .filter(permission -> StringUtils.hasText(permission.getId()))
                .collect(Collectors.toMap(Permission::getId, permission -> permission, (left, right) -> left));
    }

    private DepartPermissionSourceVO buildPermissionSource(Permission permission, String sourceType, String sourceId, String sourceName) {
        DepartPermissionSourceVO source = new DepartPermissionSourceVO();
        source.setPermissionId(permission.getId());
        source.setPermissionName(permission.getName());
        source.setPerms(permission.getPerms());
        source.setSourceType(sourceType);
        source.setSourceId(sourceId);
        source.setSourceName(sourceName);
        return source;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignUsers(String departId, List<String> userIds) {
        SysDepart depart = getActiveDepartById(departId);
        List<String> normalizedUserIds = normalizeIds(userIds);
        validateUsers(normalizedUserIds);

        List<UserDepart> activeRelations = getActiveUserDepartRelations(departId);
        Map<String, UserDepart> existingRelationMap = activeRelations.stream()
                .filter(relation -> StringUtils.hasText(relation.getUserId()))
                .collect(Collectors.toMap(UserDepart::getUserId, relation -> relation, (left, right) -> left));
        List<String> existingUserIds = new ArrayList<>(existingRelationMap.keySet());
        List<String> removedUserIds = existingUserIds.stream()
                .filter(userId -> !normalizedUserIds.contains(userId))
                .collect(Collectors.toList());
        List<String> addedUserIds = normalizedUserIds.stream()
                .filter(userId -> !existingRelationMap.containsKey(userId))
                .collect(Collectors.toList());

        removeDepartRoleUsers(departId, removedUserIds);
        clearRemovedSupervisors(departId, removedUserIds);
        removeDepartUsers(depart, removedUserIds);

        for (String userId : addedUserIds) {
            upsertUserDepartRelation(depart, userId);
        }

        ensureDefaultDepartRole(depart, null);
        log.info("为部门分配成员成功，部门ID: {}, 用户数量: {}", departId, normalizedUserIds.size());
    }

    @Override
    public List<UserVO> getDepartUsers(String departId) {
        List<String> userIds = getDepartUserIds(departId);
        if (userIds.isEmpty()) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.in(User::getId, userIds)
                .eq(User::getDeleteFlag, 0);
        return BeanUtil.copyToList(userMapper.selectList(userWrapper), UserVO.class);
    }

    @Override
    public List<String> getDepartUserIds(String departId) {
        if (!StringUtils.hasText(departId)) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<UserDepart> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDepart::getDepId, departId)
                .eq(UserDepart::getDeleteFlag, 0);
        return userDepartMapper.selectList(wrapper).stream()
                .map(UserDepart::getUserId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public List<DepartMemberRelationVO> getDepartMemberRelations(String departId) {
        getActiveDepartById(departId);
        List<UserDepart> relations = getActiveUserDepartRelations(departId);
        if (relations.isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> userIds = new HashSet<>();
        for (UserDepart relation : relations) {
            if (StringUtils.hasText(relation.getUserId())) {
                userIds.add(relation.getUserId());
            }
            if (StringUtils.hasText(relation.getSupervisorUserId())) {
                userIds.add(relation.getSupervisorUserId());
            }
        }
        Map<String, User> userMap = getActiveUserMap(userIds);
        return relations.stream()
                .map(relation -> buildMemberRelationVO(relation, userMap))
                .sorted(Comparator.comparing(vo -> Objects.toString(vo.getRealname(), Objects.toString(vo.getUsername(), "")), String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveDepartMemberRelations(DepartMemberRelationReq req) {
        if (req == null) {
            throw new IllegalArgumentException("成员关系不能为空");
        }
        SysDepart depart = getActiveDepartById(req.getDepartId());
        List<UserDepart> existingRelations = getActiveUserDepartRelations(depart.getId());
        Map<String, UserDepart> existingByUserId = toUserDepartMap(existingRelations);
        Map<String, DepartMemberRelationReq.MemberRelation> reqByUserId = normalizeMemberRelationReq(req.getMembers());

        validateRelationMembers(existingByUserId, reqByUserId);
        validateSingleDepartLeader(reqByUserId);
        validateSupervisors(existingByUserId, reqByUserId);
        validateSupervisorCycles(existingByUserId, reqByUserId);

        String tenantId = resolveTenantId(depart, null);
        if (hasDepartLeader(reqByUserId)) {
            clearDepartLeaders(depart.getId());
        }
        for (DepartMemberRelationReq.MemberRelation relation : reqByUserId.values()) {
            String userId = relation.getUserId().trim();
            int primaryDepartFlag = flagOf(relation.getPrimaryDepartFlag());
            if (primaryDepartFlag == FLAG_YES) {
                clearUserPrimaryDepart(tenantId, userId);
            }
            updateMemberRelation(depart.getId(), userId, primaryDepartFlag, flagOf(relation.getDepartLeaderFlag()),
                    trimToNull(relation.getSupervisorUserId()));
        }
    }

    @Override
    public List<DepartMemberRelationVO> getDepartLeaders(String departId) {
        return getDepartMemberRelations(departId).stream()
                .filter(relation -> flagOf(relation.getDepartLeaderFlag()) == FLAG_YES)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveDepartLeader(DepartLeaderReq req) {
        if (req == null) {
            throw new IllegalArgumentException("部门负责人不能为空");
        }
        SysDepart depart = getActiveDepartById(req.getDepartId());
        String leaderUserId = trimToNull(req.getUserId());
        if (StringUtils.hasText(leaderUserId)) {
            validateUserInDepart(depart.getId(), leaderUserId);
        }

        clearDepartLeaders(depart.getId());

        if (!StringUtils.hasText(leaderUserId)) {
            return;
        }

        LambdaUpdateWrapper<UserDepart> leaderWrapper = new LambdaUpdateWrapper<>();
        leaderWrapper.eq(UserDepart::getDepId, depart.getId())
                .eq(UserDepart::getUserId, leaderUserId)
                .eq(UserDepart::getDeleteFlag, 0)
                .set(UserDepart::getDepartLeaderFlag, FLAG_YES);
        userDepartMapper.update(new UserDepart(), leaderWrapper);
    }

    private List<UserDepart> getActiveUserDepartRelations(String departId) {
        if (!StringUtils.hasText(departId)) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<UserDepart> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDepart::getDepId, departId)
                .eq(UserDepart::getDeleteFlag, 0);
        return userDepartMapper.selectList(wrapper);
    }

    /**
     * 差量移除部门成员。先清理同一成员的历史删除行，再软删当前活动行，避免唯一索引在二次移除时冲突。
     */
    private void removeDepartUsers(SysDepart depart, List<String> userIds) {
        List<String> normalizedUserIds = normalizeIds(userIds);
        if (depart == null || normalizedUserIds.isEmpty()) {
            return;
        }

        String tenantId = resolveTenantId(depart, null);
        for (String userId : normalizedUserIds) {
            deleteDeletedUserDepartRelation(tenantId, depart.getId(), userId);
        }

        LambdaUpdateWrapper<UserDepart> deleteWrapper = new LambdaUpdateWrapper<>();
        deleteWrapper.eq(UserDepart::getDepId, depart.getId())
                .in(UserDepart::getUserId, normalizedUserIds)
                .eq(UserDepart::getDeleteFlag, 0);
        logicDeleteByWrapper(userDepartMapper, new UserDepart(), deleteWrapper, resolveDeleteBy(null));
    }

    /**
     * 新增部门成员时优先恢复历史关系，避免同一租户、用户、部门重复插入活动/删除两条关系。
     */
    private void upsertUserDepartRelation(SysDepart depart, String userId) {
        String tenantId = resolveTenantId(depart, null);
        UserDepart existingDeletedRelation = getDeletedUserDepartRelation(tenantId, depart.getId(), userId);
        if (existingDeletedRelation != null) {
            existingDeletedRelation.setDeleteFlag(FLAG_NO);
            existingDeletedRelation.setDeleteTime(null);
            existingDeletedRelation.setDeleteBy(null);
            existingDeletedRelation.setPrimaryDepartFlag(FLAG_NO);
            existingDeletedRelation.setDepartLeaderFlag(FLAG_NO);
            existingDeletedRelation.setSupervisorUserId(null);
            userDepartMapper.updateById(existingDeletedRelation);
            return;
        }

        UserDepart userDepart = new UserDepart();
        userDepart.setUserId(userId);
        userDepart.setDepId(depart.getId());
        userDepart.setPrimaryDepartFlag(FLAG_NO);
        userDepart.setDepartLeaderFlag(FLAG_NO);
        userDepart.setSupervisorUserId(null);
        userDepart.setTenantId(tenantId);
        userDepartMapper.insert(userDepart);
    }

    private UserDepart getDeletedUserDepartRelation(String tenantId, String departId, String userId) {
        LambdaQueryWrapper<UserDepart> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDepart::getDepId, departId)
                .eq(UserDepart::getUserId, userId)
                .eq(UserDepart::getDeleteFlag, 1);
        if (StringUtils.hasText(tenantId)) {
            wrapper.eq(UserDepart::getTenantId, tenantId);
        }
        wrapper.last("LIMIT 1");
        return userDepartMapper.selectOne(wrapper);
    }

    /**
     * 当前唯一索引只允许一条删除态关系。移除活动关系前物理清理旧删除态关系，保留本次删除作为最新审计记录。
     */
    private void deleteDeletedUserDepartRelation(String tenantId, String departId, String userId) {
        LambdaQueryWrapper<UserDepart> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDepart::getDepId, departId)
                .eq(UserDepart::getUserId, userId)
                .eq(UserDepart::getDeleteFlag, 1);
        if (StringUtils.hasText(tenantId)) {
            wrapper.eq(UserDepart::getTenantId, tenantId);
        }
        userDepartMapper.delete(wrapper);
    }

    /**
     * 成员被移出部门后，剩余成员不能继续把直属上级指向已移除成员。
     */
    private void clearRemovedSupervisors(String departId, List<String> removedUserIds) {
        List<String> normalizedUserIds = normalizeIds(removedUserIds);
        if (!StringUtils.hasText(departId) || normalizedUserIds.isEmpty()) {
            return;
        }

        LambdaUpdateWrapper<UserDepart> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UserDepart::getDepId, departId)
                .in(UserDepart::getSupervisorUserId, normalizedUserIds)
                .eq(UserDepart::getDeleteFlag, 0)
                .set(UserDepart::getSupervisorUserId, null);
        userDepartMapper.update(new UserDepart(), wrapper);
    }

    private Map<String, UserDepart> toUserDepartMap(List<UserDepart> relations) {
        if (relations == null || relations.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return relations.stream()
                .filter(relation -> StringUtils.hasText(relation.getUserId()))
                .collect(Collectors.toMap(UserDepart::getUserId, relation -> relation, (left, right) -> left, LinkedHashMap::new));
    }

    private Map<String, DepartMemberRelationReq.MemberRelation> normalizeMemberRelationReq(
            List<DepartMemberRelationReq.MemberRelation> relations) {
        Map<String, DepartMemberRelationReq.MemberRelation> relationMap = new LinkedHashMap<>();
        if (relations == null || relations.isEmpty()) {
            return relationMap;
        }
        for (DepartMemberRelationReq.MemberRelation relation : relations) {
            if (relation == null || !StringUtils.hasText(relation.getUserId())) {
                throw new IllegalArgumentException("成员用户ID不能为空");
            }
            String userId = relation.getUserId().trim();
            if (relationMap.containsKey(userId)) {
                throw new IllegalArgumentException("成员关系存在重复用户");
            }
            relation.setUserId(userId);
            relation.setSupervisorUserId(trimToNull(relation.getSupervisorUserId()));
            relationMap.put(userId, relation);
        }
        return relationMap;
    }

    private void validateRelationMembers(
            Map<String, UserDepart> existingByUserId,
            Map<String, DepartMemberRelationReq.MemberRelation> reqByUserId) {
        for (String userId : reqByUserId.keySet()) {
            if (!existingByUserId.containsKey(userId)) {
                throw new IllegalArgumentException("成员关系只能维护当前部门已有成员");
            }
        }
    }

    private void validateSingleDepartLeader(Map<String, DepartMemberRelationReq.MemberRelation> reqByUserId) {
        long leaderCount = reqByUserId.values().stream()
                .filter(relation -> flagOf(relation.getDepartLeaderFlag()) == FLAG_YES)
                .count();
        if (leaderCount > 1) {
            throw new IllegalArgumentException("一个部门只能设置一个负责人");
        }
    }

    private boolean hasDepartLeader(Map<String, DepartMemberRelationReq.MemberRelation> reqByUserId) {
        return reqByUserId.values().stream()
                .anyMatch(relation -> flagOf(relation.getDepartLeaderFlag()) == FLAG_YES);
    }

    private void clearDepartLeaders(String departId) {
        LambdaUpdateWrapper<UserDepart> clearWrapper = new LambdaUpdateWrapper<>();
        clearWrapper.eq(UserDepart::getDepId, departId)
                .eq(UserDepart::getDeleteFlag, 0)
                .set(UserDepart::getDepartLeaderFlag, FLAG_NO);
        userDepartMapper.update(new UserDepart(), clearWrapper);
    }

    private void validateSupervisors(
            Map<String, UserDepart> existingByUserId,
            Map<String, DepartMemberRelationReq.MemberRelation> reqByUserId) {
        for (DepartMemberRelationReq.MemberRelation relation : reqByUserId.values()) {
            String supervisorUserId = trimToNull(relation.getSupervisorUserId());
            if (!StringUtils.hasText(supervisorUserId)) {
                continue;
            }
            if (relation.getUserId().equals(supervisorUserId)) {
                throw new IllegalArgumentException("直属上级不能是自己");
            }
            if (!existingByUserId.containsKey(supervisorUserId)) {
                throw new IllegalArgumentException("直属上级必须是当前部门成员");
            }
        }
    }

    /**
     * 保存直属上级前合并待保存关系和数据库现有关系检查环路，避免形成 A -> B -> A 的组织链。
     */
    private void validateSupervisorCycles(
            Map<String, UserDepart> existingByUserId,
            Map<String, DepartMemberRelationReq.MemberRelation> reqByUserId) {
        for (String userId : reqByUserId.keySet()) {
            Set<String> visitedUserIds = new HashSet<>();
            String supervisorUserId = resolveSupervisorUserId(userId, existingByUserId, reqByUserId);
            while (StringUtils.hasText(supervisorUserId)) {
                if (userId.equals(supervisorUserId)) {
                    throw new IllegalArgumentException("直属上级关系不能形成循环");
                }
                if (!visitedUserIds.add(supervisorUserId)) {
                    throw new IllegalArgumentException("直属上级关系不能形成循环");
                }
                supervisorUserId = resolveSupervisorUserId(supervisorUserId, existingByUserId, reqByUserId);
            }
        }
    }

    private String resolveSupervisorUserId(
            String userId,
            Map<String, UserDepart> existingByUserId,
            Map<String, DepartMemberRelationReq.MemberRelation> reqByUserId) {
        DepartMemberRelationReq.MemberRelation pendingRelation = reqByUserId.get(userId);
        if (pendingRelation != null) {
            return trimToNull(pendingRelation.getSupervisorUserId());
        }
        UserDepart existingRelation = existingByUserId.get(userId);
        return existingRelation == null ? null : trimToNull(existingRelation.getSupervisorUserId());
    }

    private void validateUserInDepart(String departId, String userId) {
        LambdaQueryWrapper<UserDepart> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDepart::getDepId, departId)
                .eq(UserDepart::getUserId, userId)
                .eq(UserDepart::getDeleteFlag, 0);
        if (userDepartMapper.selectCount(wrapper) <= 0) {
            throw new IllegalArgumentException("部门负责人必须是当前部门成员");
        }
    }

    private void clearUserPrimaryDepart(String tenantId, String userId) {
        LambdaUpdateWrapper<UserDepart> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UserDepart::getUserId, userId)
                .eq(UserDepart::getDeleteFlag, 0)
                .set(UserDepart::getPrimaryDepartFlag, FLAG_NO);
        if (StringUtils.hasText(tenantId)) {
            wrapper.eq(UserDepart::getTenantId, tenantId);
        }
        userDepartMapper.update(new UserDepart(), wrapper);
    }

    private void updateMemberRelation(
            String departId,
            String userId,
            int primaryDepartFlag,
            int departLeaderFlag,
            String supervisorUserId) {
        LambdaUpdateWrapper<UserDepart> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UserDepart::getDepId, departId)
                .eq(UserDepart::getUserId, userId)
                .eq(UserDepart::getDeleteFlag, 0)
                .set(UserDepart::getPrimaryDepartFlag, primaryDepartFlag)
                .set(UserDepart::getDepartLeaderFlag, departLeaderFlag)
                .set(UserDepart::getSupervisorUserId, supervisorUserId);
        userDepartMapper.update(new UserDepart(), wrapper);
    }

    private DepartMemberRelationVO buildMemberRelationVO(UserDepart relation, Map<String, User> userMap) {
        DepartMemberRelationVO vo = new DepartMemberRelationVO();
        vo.setDepartId(relation.getDepId());
        vo.setUserId(relation.getUserId());
        vo.setPrimaryDepartFlag(flagOf(relation.getPrimaryDepartFlag()));
        vo.setDepartLeaderFlag(flagOf(relation.getDepartLeaderFlag()));
        vo.setSupervisorUserId(trimToNull(relation.getSupervisorUserId()));

        User user = userMap.get(relation.getUserId());
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setRealname(user.getRealname());
        }
        User supervisor = userMap.get(vo.getSupervisorUserId());
        if (supervisor != null) {
            vo.setSupervisorUsername(supervisor.getUsername());
            vo.setSupervisorRealname(supervisor.getRealname());
        }
        return vo;
    }

    private Map<String, User> getActiveUserMap(Set<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new LinkedHashMap<>();
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(User::getId, userIds)
                .eq(User::getDeleteFlag, 0);
        return userMapper.selectList(wrapper).stream()
                .filter(user -> StringUtils.hasText(user.getId()))
                .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left));
    }

    private int flagOf(Integer flag) {
        return Objects.equals(flag, FLAG_YES) ? FLAG_YES : FLAG_NO;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void normalizeDepart(SysDepart depart) {
        depart.setParentId(normalizeParentId(depart.getParentId()));
        depart.setDepartName(StrUtil.trimToNull(depart.getDepartName()));
        depart.setDepartNameEn(StrUtil.trimToNull(depart.getDepartNameEn()));
        depart.setDepartNameAbbr(StrUtil.trimToNull(depart.getDepartNameAbbr()));
        depart.setDescription(StrUtil.trimToNull(depart.getDescription()));
        depart.setOrgType(StrUtil.trimToNull(depart.getOrgType()));
        depart.setOrgCode(StrUtil.trimToNull(depart.getOrgCode()));
        depart.setMobile(StrUtil.trimToNull(depart.getMobile()));
        depart.setFax(StrUtil.trimToNull(depart.getFax()));
        depart.setAddress(StrUtil.trimToNull(depart.getAddress()));
        depart.setMemo(StrUtil.trimToNull(depart.getMemo()));
        depart.setStatus(StrUtil.blankToDefault(depart.getStatus(), ENABLED_STATUS));
        depart.setDepartOrder(Objects.requireNonNullElse(depart.getDepartOrder(), 0));
    }

    private String normalizeParentId(String parentId) {
        String normalizedParentId = StrUtil.trimToNull(parentId);
        if ("0".equals(normalizedParentId)) {
            return null;
        }
        return normalizedParentId;
    }

    private void validateDepart(SysDepart depart, BaseDTO<SysDepart> saveDTO) {
        Assert.notBlank(depart.getDepartName(), "机构名称不能为空");
        Assert.notBlank(depart.getOrgCode(), "机构编码不能为空");
        Assert.notBlank(depart.getOrgType(), "机构类型不能为空");
        Assert.isTrue(SysDepartOrgTypes.isValid(depart.getOrgType()), "机构类型参数不正确");
        Assert.isTrue(ENABLED_STATUS.equals(depart.getStatus()) || DISABLED_STATUS.equals(depart.getStatus()), "状态参数不正确");
        Assert.isTrue(buildDefaultRoleCode(depart, saveDTO).length() <= 64, "机构编码过长，默认部门角色编码不能超过64个字符");

        validateParent(depart);
        validateUniqueOrgCode(depart);
    }

    private void validateParent(SysDepart depart) {
        String parentId = depart.getParentId();
        if (!StringUtils.hasText(parentId)) {
            return;
        }

        if (StringUtils.hasText(depart.getId())) {
            validateParentNotSelfOrDescendant(depart.getId(), parentId);
            return;
        }

        validateParentExists(parentId);
    }

    private void validateUniqueOrgCode(SysDepart depart) {
        LambdaQueryWrapper<SysDepart> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDepart::getOrgCode, depart.getOrgCode())
                .eq(SysDepart::getDeleteFlag, 0);
        if (StringUtils.hasText(depart.getId())) {
            wrapper.ne(SysDepart::getId, depart.getId());
        }
        if (baseMapper.selectCount(wrapper) > 0) {
            throw new IllegalArgumentException("机构编码已存在");
        }
    }

    private void validateDepartExists(String departId) {
        getActiveDepartById(departId);
    }

    private SysDepart getActiveDepartById(String departId) {
        if (!StringUtils.hasText(departId)) {
            throw new IllegalArgumentException("部门ID不能为空");
        }

        SysDepart depart = baseMapper.selectById(departId);
        if (depart == null || depart.getDeleteFlag() != null && depart.getDeleteFlag() == 1) {
            throw new IllegalArgumentException("部门不存在或已被删除");
        }
        return depart;
    }

    private void validateUsers(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(User::getId, userIds)
                .eq(User::getDeleteFlag, 0);
        if (userMapper.selectCount(wrapper) != userIds.size()) {
            throw new IllegalArgumentException("存在无效用户");
        }
    }

    private void removeDepartRoleUsers(String departId, List<String> userIds) {
        List<String> normalizedUserIds = normalizeIds(userIds);
        if (normalizedUserIds.isEmpty()) {
            return;
        }

        List<String> departRoleIds = getDepartRoleIds(List.of(departId));
        if (departRoleIds.isEmpty()) {
            return;
        }

        LambdaUpdateWrapper<DepartRoleUser> wrapper = new LambdaUpdateWrapper<>();
        wrapper.in(DepartRoleUser::getDroleId, departRoleIds)
                .in(DepartRoleUser::getUserId, normalizedUserIds)
                .eq(DepartRoleUser::getDeleteFlag, 0);
        logicDeleteByWrapper(departRoleUserMapper, new DepartRoleUser(), wrapper, resolveDeleteBy(null));
    }

    private DepartRole ensureDefaultDepartRole(SysDepart depart, BaseDTO<SysDepart> saveDTO) {
        if (depart == null || !StringUtils.hasText(depart.getId())) {
            return null;
        }

        String roleCode = buildDefaultRoleCode(depart, saveDTO);
        String roleName = buildDefaultRoleName(depart);
        LambdaQueryWrapper<DepartRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DepartRole::getDepartId, depart.getId())
                .and(query -> query.eq(DepartRole::getRoleCode, roleCode)
                        .or()
                        .eq(DepartRole::getRoleCode, depart.getId())
                        .or()
                        .eq(DepartRole::getDescription, DEFAULT_ROLE_DESCRIPTION))
                .eq(DepartRole::getDeleteFlag, 0)
                .last("LIMIT 1");

        DepartRole defaultRole = departRoleMapper.selectOne(wrapper);
        if (defaultRole == null) {
            DepartRole role = new DepartRole();
            role.setId(UUID.randomUUID().toString().replace("-", ""));
            role.setDepartId(depart.getId());
            role.setRoleCode(roleCode);
            role.setRoleName(roleName);
            role.setWorkflowEnabled(FLAG_NO);
            role.setDescription(DEFAULT_ROLE_DESCRIPTION);
            role.setTenantId(resolveTenantId(depart, saveDTO));
            role.setCreateBy(depart.getCreateBy());
            role.setCreateTime(depart.getCreateTime());
            role.setDeleteFlag(0);
            departRoleMapper.insert(role);
            syncDefaultRoleUsers(depart, role.getId());
            log.info("创建部门默认角色成功，部门ID: {}, 角色ID: {}", depart.getId(), role.getId());
            return role;
        }

        boolean needUpdate = !roleName.equals(defaultRole.getRoleName())
                || !roleCode.equals(defaultRole.getRoleCode())
                || !DEFAULT_ROLE_DESCRIPTION.equals(defaultRole.getDescription());
        if (!needUpdate) {
            syncDefaultRoleUsers(depart, defaultRole.getId());
            return defaultRole;
        }

        defaultRole.setRoleCode(roleCode);
        defaultRole.setRoleName(roleName);
        defaultRole.setDescription(DEFAULT_ROLE_DESCRIPTION);
        defaultRole.setUpdateBy(depart.getUpdateBy());
        defaultRole.setUpdateTime(depart.getUpdateTime());
        departRoleMapper.updateById(defaultRole);
        syncDefaultRoleUsers(depart, defaultRole.getId());
        return defaultRole;
    }

    private String buildDefaultRoleCode(SysDepart depart, BaseDTO<SysDepart> saveDTO) {
        return DepartRoleCodes.buildDefaultRoleCode(resolveTenantId(depart, saveDTO), depart.getOrgCode());
    }

    private String resolveTenantId(SysDepart depart, BaseDTO<SysDepart> saveDTO) {
        if (depart != null && StringUtils.hasText(depart.getTenantId())) {
            return depart.getTenantId();
        }
        if (saveDTO != null && saveDTO.getContext() != null && StringUtils.hasText(saveDTO.getContext().getTenantId())) {
            return saveDTO.getContext().getTenantId();
        }
        return null;
    }

    private String buildDefaultRoleName(SysDepart depart) {
        if (StringUtils.hasText(depart.getDepartName())) {
            return depart.getDepartName().trim() + "默认角色";
        }
        return DEFAULT_ROLE_DESCRIPTION;
    }

    /**
     * 判断部门角色是否为系统维护的部门默认角色，兼容早期以部门 ID 作为角色编码的数据。
     */
    private boolean isDefaultDepartRole(DepartRole role, SysDepart depart) {
        if (role == null) {
            return false;
        }
        if (DEFAULT_ROLE_DESCRIPTION.equals(role.getDescription())) {
            return true;
        }
        if (depart == null || !StringUtils.hasText(role.getRoleCode())) {
            return false;
        }
        String expectedRoleCode = DepartRoleCodes.buildDefaultRoleCode(resolveTenantId(depart, null), depart.getOrgCode());
        return role.getRoleCode().equals(expectedRoleCode) || role.getRoleCode().equals(depart.getId());
    }

    private void syncDefaultRoleUsers(SysDepart depart, String defaultRoleId) {
        if (depart == null || !StringUtils.hasText(defaultRoleId)) {
            return;
        }

        List<String> targetUserIds = getDepartUserIds(depart.getId());
        LambdaQueryWrapper<DepartRoleUser> roleUserWrapper = new LambdaQueryWrapper<>();
        roleUserWrapper.eq(DepartRoleUser::getDroleId, defaultRoleId)
                .eq(DepartRoleUser::getDeleteFlag, 0);
        List<DepartRoleUser> existingRoleUsers = departRoleUserMapper.selectList(roleUserWrapper);

        Set<String> targetUserIdSet = Set.copyOf(targetUserIds);
        List<String> staleUserIds = existingRoleUsers.stream()
                .filter(relation -> !targetUserIdSet.contains(relation.getUserId()))
                .map(DepartRoleUser::getUserId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        if (!staleUserIds.isEmpty()) {
            LambdaUpdateWrapper<DepartRoleUser> deleteWrapper = new LambdaUpdateWrapper<>();
            deleteWrapper.eq(DepartRoleUser::getDroleId, defaultRoleId)
                    .in(DepartRoleUser::getUserId, staleUserIds)
                    .eq(DepartRoleUser::getDeleteFlag, 0);
            logicDeleteByWrapper(departRoleUserMapper, new DepartRoleUser(), deleteWrapper, resolveDeleteBy(null));
        }

        Set<String> existingUserIds = existingRoleUsers.stream()
                .map(DepartRoleUser::getUserId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        String tenantId = resolveTenantId(depart, null);
        for (String userId : targetUserIds) {
            if (existingUserIds.contains(userId)) {
                continue;
            }
            DepartRoleUser roleUser = new DepartRoleUser();
            roleUser.setUserId(userId);
            roleUser.setDroleId(defaultRoleId);
            roleUser.setTenantId(tenantId);
            departRoleUserMapper.insert(roleUser);
        }
    }

    private void refreshLeafFlags() {
        LambdaQueryWrapper<SysDepart> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDepart::getDeleteFlag, 0);
        List<SysDepart> departs = baseMapper.selectList(wrapper);
        if (departs.isEmpty()) {
            return;
        }

        Set<String> parentIds = departs.stream()
                .map(SysDepart::getParentId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        for (SysDepart depart : departs) {
            Boolean leaf = !parentIds.contains(depart.getId());
            if (!Objects.equals(depart.getIzLeaf(), leaf)) {
                SysDepart update = new SysDepart();
                update.setId(depart.getId());
                update.setIzLeaf(leaf);
                baseMapper.updateById(update);
            }
        }
    }

    private List<String> resolveDepartIds(BaseDTO<SysDepart> deleteDTO) {
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

    private List<String> getDepartRoleIds(List<String> departIds) {
        if (departIds == null || departIds.isEmpty()) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<DepartRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(DepartRole::getDepartId, departIds)
                .eq(DepartRole::getDeleteFlag, 0);
        return departRoleMapper.selectList(wrapper).stream()
                .map(DepartRole::getId)
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

    @SuppressWarnings("unchecked")
    private QueryWrapper<SysDepart> resolveQueryWrapper(BaseDTO<SysDepart> baseDTO) {
        if (baseDTO == null) {
            return new QueryWrapper<>();
        }

        QueryWrapper<SysDepart> wrapper = (QueryWrapper<SysDepart>) baseDTO.getQueryWrapper();
        if (wrapper == null) {
            wrapper = new QueryWrapper<>();
            baseDTO.setQueryWrapper(wrapper);
        }
        return wrapper;
    }

    private void applyDepartOrder(QueryWrapper<SysDepart> wrapper) {
        wrapper.orderByAsc("depart_order")
                .orderByAsc("create_time");
    }

    private void markRollbackIfFailed(BaseResult<?> result) {
        if (result == null || result.getCode() == null || result.getCode() != 200) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
    }

    /**
     * 对关联表执行逻辑删除，避免物理删除破坏审计链路。
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
