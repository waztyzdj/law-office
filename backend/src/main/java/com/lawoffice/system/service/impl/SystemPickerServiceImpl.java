package com.lawoffice.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawoffice.framework.config.TenantContextHolder;
import com.lawoffice.system.entity.Role;
import com.lawoffice.system.entity.SysDepart;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.entity.UserDepart;
import com.lawoffice.system.entity.UserRole;
import com.lawoffice.system.entity.UserTenant;
import com.lawoffice.system.mapper.RoleMapper;
import com.lawoffice.system.mapper.SysDepartMapper;
import com.lawoffice.system.mapper.UserDepartMapper;
import com.lawoffice.system.mapper.UserMapper;
import com.lawoffice.system.mapper.UserRoleMapper;
import com.lawoffice.system.mapper.UserTenantMapper;
import com.lawoffice.system.service.ISystemPickerService;
import com.lawoffice.system.vo.RoleVO;
import com.lawoffice.system.vo.SysDepartVO;
import com.lawoffice.system.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SystemPickerServiceImpl implements ISystemPickerService {

    private static final Integer NOT_DELETED = 0;
    private static final Integer USER_ENABLED = 1;
    private static final String TENANT_USER_ENABLED = "1";
    private static final String DEPART_ENABLED = "1";

    private final UserMapper userMapper;
    private final UserTenantMapper userTenantMapper;
    private final SysDepartMapper sysDepartMapper;
    private final UserDepartMapper userDepartMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;

    @Override
    public List<UserVO> listUsers() {
        return listActiveUsers(getTenantMemberUserIds(requireTenantId()));
    }

    @Override
    public List<SysDepartVO> listDeparts() {
        String tenantId = requireTenantId();
        LambdaQueryWrapper<SysDepart> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDepart::getTenantId, tenantId)
                .eq(SysDepart::getDeleteFlag, NOT_DELETED)
                .eq(SysDepart::getStatus, DEPART_ENABLED)
                .orderByAsc(SysDepart::getDepartOrder)
                .orderByAsc(SysDepart::getCreateTime);
        return BeanUtil.copyToList(sysDepartMapper.selectList(wrapper), SysDepartVO.class);
    }

    @Override
    public List<RoleVO> listRoles() {
        String tenantId = requireTenantId();
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Role::getTenantId, tenantId)
                .eq(Role::getDeleteFlag, NOT_DELETED)
                .orderByAsc(Role::getCreateTime);
        return BeanUtil.copyToList(roleMapper.selectList(wrapper), RoleVO.class);
    }

    @Override
    public List<UserVO> listDepartUsers(String departId) {
        if (!StringUtils.hasText(departId)) {
            throw new IllegalArgumentException("部门ID不能为空");
        }
        String tenantId = requireTenantId();
        SysDepart depart = sysDepartMapper.selectById(departId);
        if (depart == null
                || !tenantId.equals(depart.getTenantId())
                || isDeleted(depart.getDeleteFlag())) {
            return new ArrayList<>();
        }

        Set<String> tenantUserIds = new LinkedHashSet<>(getTenantMemberUserIds(tenantId));
        if (tenantUserIds.isEmpty()) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<UserDepart> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDepart::getTenantId, tenantId)
                .eq(UserDepart::getDepId, departId)
                .eq(UserDepart::getDeleteFlag, NOT_DELETED)
                .in(UserDepart::getUserId, tenantUserIds);
        List<String> userIds = userDepartMapper.selectList(wrapper).stream()
                .map(UserDepart::getUserId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        return listActiveUsers(userIds);
    }

    @Override
    public List<UserVO> listRoleUsers(String roleId) {
        if (!StringUtils.hasText(roleId)) {
            throw new IllegalArgumentException("角色ID不能为空");
        }
        String tenantId = requireTenantId();
        Role role = roleMapper.selectById(roleId);
        if (role == null
                || !tenantId.equals(role.getTenantId())
                || isDeleted(role.getDeleteFlag())) {
            return new ArrayList<>();
        }

        Set<String> tenantUserIds = new LinkedHashSet<>(getTenantMemberUserIds(tenantId));
        if (tenantUserIds.isEmpty()) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<UserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserRole::getTenantId, tenantId)
                .eq(UserRole::getRoleId, roleId)
                .eq(UserRole::getDeleteFlag, NOT_DELETED)
                .in(UserRole::getUserId, tenantUserIds);
        List<String> userIds = userRoleMapper.selectList(wrapper).stream()
                .map(UserRole::getUserId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        return listActiveUsers(userIds);
    }

    /**
     * 选择器接口不走菜单权限，必须显式依赖当前租户上下文来收紧数据边界。
     */
    private String requireTenantId() {
        String tenantId = TenantContextHolder.getCurrentTenantId();
        if (!StringUtils.hasText(tenantId)) {
            throw new IllegalArgumentException("当前租户不能为空");
        }
        return tenantId;
    }

    /**
     * 用户基础表不是租户表，需要先通过用户租户关系表圈定有效成员。
     */
    private List<String> getTenantMemberUserIds(String tenantId) {
        LambdaQueryWrapper<UserTenant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserTenant::getTenantId, tenantId)
                .eq(UserTenant::getStatus, TENANT_USER_ENABLED)
                .eq(UserTenant::getDeleteFlag, NOT_DELETED);
        return userTenantMapper.selectList(wrapper).stream()
                .map(UserTenant::getUserId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<UserVO> listActiveUsers(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(User::getId, userIds)
                .eq(User::getDeleteFlag, NOT_DELETED)
                .eq(User::getStatus, USER_ENABLED)
                .orderByAsc(User::getUsername);
        return BeanUtil.copyToList(userMapper.selectList(wrapper), UserVO.class);
    }

    private boolean isDeleted(Integer deleteFlag) {
        return deleteFlag != null && !NOT_DELETED.equals(deleteFlag);
    }
}
