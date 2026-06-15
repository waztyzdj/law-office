package com.lawoffice.document.service.impl;

import static com.lawoffice.document.constant.DocumentCenterConstants.*;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lawoffice.system.entity.Role;
import com.lawoffice.system.entity.SysDepart;
import com.lawoffice.system.entity.Tenant;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.entity.UserTenant;
import com.lawoffice.system.mapper.RoleMapper;
import com.lawoffice.system.mapper.SysDepartMapper;
import com.lawoffice.system.mapper.TenantMapper;
import com.lawoffice.system.mapper.UserMapper;
import com.lawoffice.system.mapper.UserTenantMapper;
import com.lawoffice.document.req.DocumentShareTargetReq;
import com.lawoffice.document.service.IDocumentShareTargetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DocumentShareTargetServiceImpl implements IDocumentShareTargetService {

    private final TenantMapper tenantMapper;
    private final UserMapper userMapper;
    private final UserTenantMapper userTenantMapper;
    private final SysDepartMapper sysDepartMapper;
    private final RoleMapper roleMapper;

    @Override
    public void validateShareTarget(DocumentShareTargetReq target, String tenantId) {
        if (TARGET_TENANT.equals(target.getTargetType())) {
            validateTenantTarget(target.getTargetId(), tenantId);
            return;
        }
        if (TARGET_USER.equals(target.getTargetType())) {
            validateUserTarget(target.getTargetId(), tenantId);
            return;
        }
        if (TARGET_DEPART.equals(target.getTargetType())) {
            validateDepartTarget(target.getTargetId(), tenantId);
            return;
        }
        validateRoleTarget(target.getTargetId(), tenantId);
    }

    @Override
    public String resolveTargetName(String targetType, String targetId) {
        if (TARGET_TENANT.equals(targetType)) {
            Tenant tenant = tenantMapper.selectById(targetId);
            return tenant == null ? targetId : tenant.getName();
        }
        if (TARGET_USER.equals(targetType)) {
            User user = userMapper.selectById(targetId);
            return user == null ? targetId : (StringUtils.hasText(user.getRealname()) ? user.getRealname() : user.getUsername());
        }
        if (TARGET_DEPART.equals(targetType)) {
            SysDepart depart = sysDepartMapper.selectById(targetId);
            return depart == null ? targetId : depart.getDepartName();
        }
        Role role = roleMapper.selectById(targetId);
        return role == null ? targetId : role.getRoleName();
    }

    @Override
    public String resolveTargetTypeText(String targetType) {
        if (TARGET_TENANT.equals(targetType)) {
            return "租户";
        }
        if (TARGET_USER.equals(targetType)) {
            return "人员";
        }
        if (TARGET_DEPART.equals(targetType)) {
            return "部门";
        }
        if (TARGET_ROLE.equals(targetType)) {
            return "角色";
        }
        return targetType;
    }

    /**
     * 租户共享只能共享给当前租户，不能由前端传入其他租户 ID。
     */
    private void validateTenantTarget(String targetId, String tenantId) {
        if (!Objects.equals(targetId, tenantId)) {
            throw new IllegalArgumentException("只能共享给当前租户");
        }
        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null || Objects.equals(tenant.getDeleteFlag(), 1)) {
            throw new IllegalArgumentException("共享租户不存在");
        }
    }

    /**
     * 用户共享必须校验用户存在且属于当前租户，避免跨租户授权。
     */
    private void validateUserTarget(String targetId, String tenantId) {
        User user = userMapper.selectById(targetId);
        if (user == null || Objects.equals(user.getDeleteFlag(), 1)) {
            throw new IllegalArgumentException("共享用户不存在");
        }
        Long tenantCount = userTenantMapper.selectCount(Wrappers.lambdaQuery(UserTenant.class)
                .eq(UserTenant::getTenantId, tenantId)
                .eq(UserTenant::getUserId, targetId)
                .eq(UserTenant::getDeleteFlag, 0));
        if (tenantCount == 0) {
            throw new IllegalArgumentException("只能共享给当前租户用户");
        }
    }

    /**
     * 部门共享必须限定当前租户未删除部门。
     */
    private void validateDepartTarget(String targetId, String tenantId) {
        SysDepart depart = sysDepartMapper.selectOne(Wrappers.lambdaQuery(SysDepart.class)
                .eq(SysDepart::getId, targetId)
                .eq(SysDepart::getTenantId, tenantId)
                .eq(SysDepart::getDeleteFlag, 0)
                .last("LIMIT 1"));
        if (depart == null) {
            throw new IllegalArgumentException("共享部门不存在");
        }
    }

    /**
     * 角色共享必须限定当前租户未删除角色。
     */
    private void validateRoleTarget(String targetId, String tenantId) {
        Role role = roleMapper.selectOne(Wrappers.lambdaQuery(Role.class)
                .eq(Role::getId, targetId)
                .eq(Role::getTenantId, tenantId)
                .eq(Role::getDeleteFlag, 0)
                .last("LIMIT 1"));
        if (role == null) {
            throw new IllegalArgumentException("共享角色不存在");
        }
    }
}
