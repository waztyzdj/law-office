package com.lawoffice.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.system.entity.DepartPermission;
import com.lawoffice.system.entity.DepartRole;
import com.lawoffice.system.entity.DepartRolePermission;
import com.lawoffice.system.entity.DepartRoleUser;
import com.lawoffice.system.entity.Permission;
import com.lawoffice.system.entity.SysDepart;
import com.lawoffice.system.entity.UserDepart;
import com.lawoffice.system.mapper.DepartPermissionMapper;
import com.lawoffice.system.mapper.DepartRoleMapper;
import com.lawoffice.system.mapper.DepartRolePermissionMapper;
import com.lawoffice.system.mapper.DepartRoleUserMapper;
import com.lawoffice.system.mapper.PermissionMapper;
import com.lawoffice.system.mapper.SysDepartMapper;
import com.lawoffice.system.mapper.UserDepartMapper;
import com.lawoffice.system.service.ISysDepartService;
import com.lawoffice.system.vo.SysDepartVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SysDepartServiceImpl extends BaseServiceImpl<SysDepartMapper, SysDepart, SysDepartVO> implements ISysDepartService {

    private static final String DEFAULT_ROLE_DESCRIPTION = "部门默认角色";

    @Autowired
    private DepartRoleMapper departRoleMapper;

    @Autowired
    private DepartPermissionMapper departPermissionMapper;

    @Autowired
    private DepartRolePermissionMapper departRolePermissionMapper;

    @Autowired
    private DepartRoleUserMapper departRoleUserMapper;

    @Autowired
    private PermissionMapper permissionMapper;

    @Autowired
    private UserDepartMapper userDepartMapper;

    @Override
    protected void doAfterSave(BaseDTO<SysDepart> saveDTO, SysDepartVO vo) {
        if (saveDTO == null) {
            return;
        }
        SysDepart depart = saveDTO.getEntity();
        ensureDefaultDepartRole(depart);
        normalizeRootParentId(depart);
    }

    @Override
    protected void doAfterDelete(BaseDTO<SysDepart> deleteDTO) {
        List<String> departIds = resolveDepartIds(deleteDTO);
        if (departIds.isEmpty()) {
            return;
        }

        List<String> departRoleIds = getDepartRoleIds(departIds);

        LambdaQueryWrapper<UserDepart> userDepartWrapper = new LambdaQueryWrapper<>();
        userDepartWrapper.in(UserDepart::getDepId, departIds);
        userDepartMapper.delete(userDepartWrapper);

        if (!departRoleIds.isEmpty()) {
            LambdaQueryWrapper<DepartRoleUser> roleUserWrapper = new LambdaQueryWrapper<>();
            roleUserWrapper.in(DepartRoleUser::getDroleId, departRoleIds);
            departRoleUserMapper.delete(roleUserWrapper);

            LambdaQueryWrapper<DepartRolePermission> rolePermissionWrapper = new LambdaQueryWrapper<>();
            rolePermissionWrapper.in(DepartRolePermission::getRoleId, departRoleIds);
            departRolePermissionMapper.delete(rolePermissionWrapper);
        }

        LambdaQueryWrapper<DepartPermission> departPermissionWrapper = new LambdaQueryWrapper<>();
        departPermissionWrapper.in(DepartPermission::getDepartId, departIds);
        departPermissionMapper.delete(departPermissionWrapper);

        LambdaQueryWrapper<DepartRole> departRoleWrapper = new LambdaQueryWrapper<>();
        departRoleWrapper.in(DepartRole::getDepartId, departIds);
        departRoleMapper.delete(departRoleWrapper);

        log.info("删除部门关联数据成功，部门数量: {}, 部门角色数量: {}", departIds.size(), departRoleIds.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(String departId, List<String> roleIds) {
        List<String> normalizedRoleIds = normalizeIds(roleIds);
        if (!StringUtils.hasText(departId) || normalizedRoleIds.isEmpty()) {
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

        LambdaQueryWrapper<DepartRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DepartRole::getDepartId, departId)
                .eq(DepartRole::getDeleteFlag, 0);
        return departRoleMapper.selectList(wrapper);
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

        LambdaQueryWrapper<DepartRoleUser> roleUserWrapper = new LambdaQueryWrapper<>();
        roleUserWrapper.in(DepartRoleUser::getDroleId, normalizedRoleIds);
        departRoleUserMapper.delete(roleUserWrapper);

        LambdaQueryWrapper<DepartRolePermission> rolePermissionWrapper = new LambdaQueryWrapper<>();
        rolePermissionWrapper.in(DepartRolePermission::getRoleId, normalizedRoleIds);
        departRolePermissionMapper.delete(rolePermissionWrapper);

        LambdaQueryWrapper<DepartRole> roleWrapper = new LambdaQueryWrapper<>();
        roleWrapper.eq(DepartRole::getDepartId, departId)
                .in(DepartRole::getId, normalizedRoleIds);
        departRoleMapper.delete(roleWrapper);

        log.info("移除部门角色成功，部门ID: {}, 移除角色数量: {}", departId, normalizedRoleIds.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignPermissions(String departId, List<String> permissionIds) {
        LambdaQueryWrapper<DepartPermission> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(DepartPermission::getDepartId, departId);
        departPermissionMapper.delete(deleteWrapper);

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
        LambdaQueryWrapper<DepartPermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DepartPermission::getDepartId, departId);
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
        permissionWrapper.in(Permission::getId, permissionIds);
        return permissionMapper.selectList(permissionWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removePermissions(String departId, List<String> permissionIds) {
        List<String> normalizedPermissionIds = normalizeIds(permissionIds);
        if (!StringUtils.hasText(departId) || normalizedPermissionIds.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<DepartPermission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DepartPermission::getDepartId, departId)
                .in(DepartPermission::getPermissionId, normalizedPermissionIds);
        departPermissionMapper.delete(wrapper);

        log.info("移除部门权限成功，部门ID: {}, 移除权限数量: {}", departId, normalizedPermissionIds.size());
    }

    private void ensureDefaultDepartRole(SysDepart depart) {
        if (depart == null || !StringUtils.hasText(depart.getId())) {
            return;
        }

        String roleName = buildDefaultRoleName(depart);
        LambdaQueryWrapper<DepartRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DepartRole::getDepartId, depart.getId())
                .eq(DepartRole::getRoleCode, depart.getId())
                .eq(DepartRole::getDeleteFlag, 0)
                .last("LIMIT 1");

        DepartRole defaultRole = departRoleMapper.selectOne(wrapper);
        if (defaultRole == null) {
            DepartRole role = new DepartRole();
            role.setId(UUID.randomUUID().toString().replace("-", ""));
            role.setDepartId(depart.getId());
            role.setRoleCode(depart.getId());
            role.setRoleName(roleName);
            role.setDescription(DEFAULT_ROLE_DESCRIPTION);
            role.setTenantId(depart.getTenantId());
            role.setCreateBy(depart.getCreateBy());
            role.setCreateTime(depart.getCreateTime());
            role.setDeleteFlag(0);
            departRoleMapper.insert(role);
            log.info("创建部门默认角色成功，部门ID: {}, 角色ID: {}", depart.getId(), role.getId());
            return;
        }

        boolean needUpdate = !roleName.equals(defaultRole.getRoleName())
                || !DEFAULT_ROLE_DESCRIPTION.equals(defaultRole.getDescription());
        if (!needUpdate) {
            return;
        }

        defaultRole.setRoleName(roleName);
        defaultRole.setDescription(DEFAULT_ROLE_DESCRIPTION);
        defaultRole.setUpdateBy(depart.getUpdateBy());
        defaultRole.setUpdateTime(depart.getUpdateTime());
        departRoleMapper.updateById(defaultRole);
    }

    private void normalizeRootParentId(SysDepart depart) {
        if (depart == null || !StringUtils.hasText(depart.getId())) {
            return;
        }

        if (StringUtils.hasText(depart.getParentId()) && !"0".equals(depart.getParentId())) {
            return;
        }

        LambdaUpdateWrapper<SysDepart> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(SysDepart::getId, depart.getId())
                .set(SysDepart::getParentId, null);
        baseMapper.update(null, updateWrapper);
    }

    private String buildDefaultRoleName(SysDepart depart) {
        if (StringUtils.hasText(depart.getDepartName())) {
            return depart.getDepartName().trim() + "默认角色";
        }
        return DEFAULT_ROLE_DESCRIPTION;
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
        wrapper.in(DepartRole::getDepartId, departIds);
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
}
