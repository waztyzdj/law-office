package com.lawoffice.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.system.entity.*;
import com.lawoffice.system.mapper.*;
import com.lawoffice.system.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserServiceImpl extends BaseServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private UserRoleMapper userRoleMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private UserDepartMapper userDepartMapper;

    @Autowired
    private SysDepartMapper sysDepartMapper;

    @Autowired
    private UserTenantMapper userTenantMapper;

    @Autowired
    private TenantMapper tenantMapper;

    @Autowired
    private RolePermissionMapper rolePermissionMapper;

    @Autowired
    private PermissionMapper permissionMapper;
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private com.lawoffice.system.service.ITokenService tokenService;
    
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    protected void doBeforeSave(BaseDTO<User> saveDTO) {
        User user = saveDTO.getEntity();
        
        if (user == null) {
            return;
        }
        
        if (user.getId() == null || user.getId().isEmpty()) {
            log.info("新增用户，用户名: {}", user.getUsername());
            
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(User::getUsername, user.getUsername())
                   .eq(User::getDeleteFlag, 0);
            if (baseMapper.selectCount(wrapper) > 0) {
                throw new RuntimeException("用户名已存在");
            }
            
            if (StringUtils.hasText(user.getPhone())) {
                LambdaQueryWrapper<User> phoneWrapper = new LambdaQueryWrapper<>();
                phoneWrapper.eq(User::getPhone, user.getPhone())
                           .eq(User::getDeleteFlag, 0);
                if (baseMapper.selectCount(phoneWrapper) > 0) {
                    throw new RuntimeException("手机号已被使用");
                }
            }
            
            if (StringUtils.hasText(user.getIdCard())) {
                LambdaQueryWrapper<User> idCardWrapper = new LambdaQueryWrapper<>();
                idCardWrapper.eq(User::getIdCard, user.getIdCard())
                            .eq(User::getDeleteFlag, 0);
                if (baseMapper.selectCount(idCardWrapper) > 0) {
                    throw new RuntimeException("身份证号已被使用");
                }
            }
            
            if (StringUtils.hasText(user.getPassword())) {
                user.setPassword(passwordEncoder.encode(user.getPassword()));
            }
            
            if (user.getStatus() == null) {
                user.setStatus(1);
            }
        } else {
            log.info("修改用户，用户ID: {}", user.getId());
            
            if (StringUtils.hasText(user.getPassword())) {
                user.setPassword(passwordEncoder.encode(user.getPassword()));
            }
        }
    }

    @Override
    protected void doAfterSave(BaseDTO<User> saveDTO, User entity) {
        log.info("保存用户成功，用户ID: {}", entity.getId());
    }

    @Override
    public boolean verifyPassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    @Override
    public void resetPassword(String userId, String newPassword) {
        User user = new User();
        user.setId(userId);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdateTime(java.time.LocalDateTime.now());
        user.setUpdateBy("system");
        // 使用 ServiceImpl 提供的 updateById 方法
        this.updateById(user);
        log.info("重置用户密码成功，用户ID: {}", userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(String userId, List<String> roleIds) {
        // 先删除用户现有的所有角色
        LambdaQueryWrapper<UserRole> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(UserRole::getUserId, userId);
        userRoleMapper.delete(deleteWrapper);

        // 批量插入新的角色关联
        if (roleIds != null && !roleIds.isEmpty()) {
            List<UserRole> userRoles = roleIds.stream()
                .map(roleId -> {
                    UserRole userRole = new UserRole();
                    userRole.setUserId(userId);
                    userRole.setRoleId(roleId);
                    return userRole;
                })
                .collect(Collectors.toList());

            for (UserRole userRole : userRoles) {
                userRoleMapper.insert(userRole);
            }
        }

        log.info("为用户分配角色成功，用户ID: {}, 角色数量: {}", userId, roleIds == null ? 0 : roleIds.size());
    }

    @Override
    public List<Role> getUserRoles(String userId) {
        // 查询用户的角色ID列表
        LambdaQueryWrapper<UserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserRole::getUserId, userId);
        List<UserRole> userRoles = userRoleMapper.selectList(wrapper);

        if (userRoles.isEmpty()) {
            return new ArrayList<>();
        }

        // 根据角色ID查询角色详情
        List<String> roleIds = userRoles.stream()
            .map(UserRole::getRoleId)
            .collect(Collectors.toList());

        LambdaQueryWrapper<Role> roleWrapper = new LambdaQueryWrapper<>();
        roleWrapper.in(Role::getId, roleIds);
        return roleMapper.selectList(roleWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeRoles(String userId, List<String> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<UserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserRole::getUserId, userId)
               .in(UserRole::getRoleId, roleIds);
        userRoleMapper.delete(wrapper);

        log.info("移除用户角色成功，用户ID: {}, 移除角色数量: {}", userId, roleIds.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignDeparts(String userId, List<String> departIds) {
        // 先删除用户现有的所有部门
        LambdaQueryWrapper<UserDepart> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(UserDepart::getUserId, userId);
        userDepartMapper.delete(deleteWrapper);

        // 批量插入新的部门关联
        if (departIds != null && !departIds.isEmpty()) {
            List<UserDepart> userDeparts = departIds.stream()
                .map(departId -> {
                    UserDepart userDepart = new UserDepart();
                    userDepart.setUserId(userId);
                    userDepart.setDepId(departId);
                    return userDepart;
                })
                .collect(Collectors.toList());

            for (UserDepart userDepart : userDeparts) {
                userDepartMapper.insert(userDepart);
            }
        }

        log.info("为用户分配部门成功，用户ID: {}, 部门数量: {}", userId, departIds == null ? 0 : departIds.size());
    }

    @Override
    public List<SysDepart> getUserDeparts(String userId) {
        // 查询用户的部门ID列表
        LambdaQueryWrapper<UserDepart> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDepart::getUserId, userId);
        List<UserDepart> userDeparts = userDepartMapper.selectList(wrapper);

        if (userDeparts.isEmpty()) {
            return new ArrayList<>();
        }

        // 根据部门ID查询部门详情
        List<String> departIds = userDeparts.stream()
            .map(UserDepart::getDepId)
            .collect(Collectors.toList());

        LambdaQueryWrapper<SysDepart> departWrapper = new LambdaQueryWrapper<>();
        departWrapper.in(SysDepart::getId, departIds);
        return sysDepartMapper.selectList(departWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeDeparts(String userId, List<String> departIds) {
        if (departIds == null || departIds.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<UserDepart> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDepart::getUserId, userId)
               .in(UserDepart::getDepId, departIds);
        userDepartMapper.delete(wrapper);

        log.info("移除用户部门成功，用户ID: {}, 移除部门数量: {}", userId, departIds.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignTenants(String userId, List<String> tenantIds) {
        // 先删除用户现有的所有租户
        LambdaQueryWrapper<UserTenant> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(UserTenant::getUserId, userId);
        userTenantMapper.delete(deleteWrapper);

        // 批量插入新的租户关联
        if (tenantIds != null && !tenantIds.isEmpty()) {
            List<UserTenant> userTenants = tenantIds.stream()
                .map(tenantId -> {
                    UserTenant userTenant = new UserTenant();
                    userTenant.setUserId(userId);
                    userTenant.setTenantId(tenantId);
                    userTenant.setStatus("1"); // 默认状态为正常
                    return userTenant;
                })
                .collect(Collectors.toList());

            for (UserTenant userTenant : userTenants) {
                userTenantMapper.insert(userTenant);
            }
        }

        log.info("为用户分配租户成功，用户ID: {}, 租户数量: {}", userId, tenantIds == null ? 0 : tenantIds.size());
    }

    @Override
    public List<Tenant> getUserTenants(String userId) {
        // 查询用户的租户ID列表
        LambdaQueryWrapper<UserTenant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserTenant::getUserId, userId);
        List<UserTenant> userTenants = userTenantMapper.selectList(wrapper);

        if (userTenants.isEmpty()) {
            return new ArrayList<>();
        }

        // 根据租户ID查询租户详情
        List<String> tenantIds = userTenants.stream()
            .map(UserTenant::getTenantId)
            .collect(Collectors.toList());

        LambdaQueryWrapper<Tenant> tenantWrapper = new LambdaQueryWrapper<>();
        tenantWrapper.in(Tenant::getId, tenantIds);
        return tenantMapper.selectList(tenantWrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeTenants(String userId, List<String> tenantIds) {
        if (tenantIds == null || tenantIds.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<UserTenant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserTenant::getUserId, userId)
               .in(UserTenant::getTenantId, tenantIds);
        userTenantMapper.delete(wrapper);

        log.info("移除用户租户成功，用户ID: {}, 移除租户数量: {}", userId, tenantIds.size());
    }

    @Override
    public List<Permission> getUserPermissions(String userId) {
        // 1. 获取用户的角色ID列表
        LambdaQueryWrapper<UserRole> userRoleWrapper = new LambdaQueryWrapper<>();
        userRoleWrapper.eq(UserRole::getUserId, userId);
        List<UserRole> userRoles = userRoleMapper.selectList(userRoleWrapper);

        if (userRoles.isEmpty()) {
            return new ArrayList<>();
        }

        // 2. 获取所有角色的ID
        List<String> roleIds = userRoles.stream()
            .map(UserRole::getRoleId)
            .distinct()
            .collect(Collectors.toList());

        // 3. 根据角色ID查询角色权限关联
        LambdaQueryWrapper<RolePermission> rolePermWrapper = new LambdaQueryWrapper<>();
        rolePermWrapper.in(RolePermission::getRoleId, roleIds);
        List<RolePermission> rolePermissions = rolePermissionMapper.selectList(rolePermWrapper);

        if (rolePermissions.isEmpty()) {
            return new ArrayList<>();
        }

        // 4. 获取所有权限ID
        List<String> permissionIds = rolePermissions.stream()
            .map(RolePermission::getPermissionId)
            .distinct()
            .collect(Collectors.toList());

        // 5. 根据权限ID查询权限详情
        LambdaQueryWrapper<Permission> permWrapper = new LambdaQueryWrapper<>();
        permWrapper.in(Permission::getId, permissionIds)
                   .eq(Permission::getStatus, "1") // 只查询有效的权限
                   .orderByAsc(Permission::getSortNo);
        return permissionMapper.selectList(permWrapper);
    }

    @Override
    public List<String> getUserPermissionCodes(String userId) {
        // 获取用户权限列表，然后提取perms字段
        List<Permission> permissions = getUserPermissions(userId);
        
        return permissions.stream()
            .map(Permission::getPerms)
            .filter(StringUtils::hasText) // 过滤掉空的perms
            .distinct()
            .collect(Collectors.toList());
    }

    @Override
    public java.util.Map<String, Object> login(String username, String password) {
        // 1. 查询用户信息（包含密码字段）
        User user = userMapper.selectByUsernameForLogin(username);
        
        if (user == null) {
            log.warn("登录失败：用户名不存在 - {}", username);
            throw new RuntimeException("用户名或密码错误");
        }
        
        // 2. 检查用户状态
        if (user.getStatus() != null && user.getStatus() == 0) {
            log.warn("登录失败：用户已被禁用 - {}", username);
            throw new RuntimeException("用户已被禁用，请联系管理员");
        }
        
        // 3. 验证密码
        if (!verifyPassword(password, user.getPassword())) {
            log.warn("登录失败：密码错误 - {}", username);
            throw new RuntimeException("用户名或密码错误");
        }
        
        // 4. 获取用户权限列表
        List<String> permissionCodes = getUserPermissionCodes(user.getId());
        log.info("用户 {} 的权限列表: {}", username, permissionCodes);
        
        // 5. 获取用户角色列表
        List<Role> roles = getUserRoles(user.getId());
        List<String> roleCodes = roles.stream()
                .map(Role::getRoleCode)
                .collect(Collectors.toList());
        log.info("用户 {} 的角色列表: {}", username, roleCodes);
        
        // 6. 强制清除所有旧的 Redis 缓存（包括 Token、权限、角色）
        tokenService.forceLogout(username);
        
        // 7. 生成并存储Token到Redis
        String token = tokenService.generateAndStoreToken(
                username,
                user.getId(),
                user.getRealname(),
                permissionCodes,
                roleCodes
        );
        
        // 8. 构建返回结果（只返回token）
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("token", token);
        
        log.info("用户登录成功：{}", username);
        return result;
    }

    @Override
    public User getCurrentUserInfo(String username) {
        if (!StringUtils.hasText(username)) {
            throw new RuntimeException("未登录或登录已过期");
        }
        
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username)
               .eq(User::getDeleteFlag, 0);
        User user = userMapper.selectOne(wrapper);
        
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        return user;
    }

    @Override
    public void logout(String token, String username) {
        if (token != null && !token.isEmpty()) {
            // 删除Redis中的Token和权限信息
            tokenService.removeToken(token);
            log.info("用户登出：{}", username != null ? username : "unknown");
        } else {
            log.info("用户登出：无有效Token");
        }
    }

    @Override
    public void changePassword(String username, String oldPassword, String newPassword) {
        // 1. 查询用户信息（包含密码字段）
        User user = userMapper.selectByUsernameForLogin(username);
        
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        // 2. 验证旧密码
        if (!verifyPassword(oldPassword, user.getPassword())) {
            throw new RuntimeException("旧密码错误");
        }
        
        // 3. 重置密码
        resetPassword(user.getId(), newPassword);
        log.info("用户修改密码成功：{}", username);
    }

    @Override
    public com.lawoffice.system.dto.UserInfoDTO getCurrentUserDetailInfo(String username) {
        if (!StringUtils.hasText(username)) {
            throw new RuntimeException("未登录或登录已过期");
        }
        
        // 1. 获取用户基本信息
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username)
               .eq(User::getDeleteFlag, 0);
        User user = userMapper.selectOne(wrapper);
        
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        // 2. 获取用户权限列表
        List<String> permissionCodes = getUserPermissionCodes(user.getId());
        log.info("用户 {} 的权限列表: {}", username, permissionCodes);
        
        // 3. 获取用户角色列表
        List<Role> roles = getUserRoles(user.getId());
        List<String> roleCodes = roles.stream()
                .map(Role::getRoleCode)
                .collect(Collectors.toList());
        log.info("用户 {} 的角色列表: {}", username, roleCodes);
        
        // 4. 构建返回结果
        com.lawoffice.system.dto.UserInfoDTO userInfoDTO = new com.lawoffice.system.dto.UserInfoDTO();
        userInfoDTO.setUserId(user.getId());
        userInfoDTO.setUsername(user.getUsername());
        userInfoDTO.setRealName(user.getRealname());
        userInfoDTO.setPermissions(permissionCodes);
        userInfoDTO.setRoles(roleCodes);
        // homePath 可以根据业务需求设置
        userInfoDTO.setHomePath("/system/user");
        
        log.info("获取用户详细信息成功：{}, 权限数量: {}, 角色数量: {}", username, permissionCodes.size(), roleCodes.size());
        return userInfoDTO;
    }
}
