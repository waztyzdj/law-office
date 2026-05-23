package com.lawoffice.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.system.vo.UserInfoVO;
import com.lawoffice.system.entity.*;
import com.lawoffice.system.mapper.*;
import com.lawoffice.system.service.IUserService;
import com.lawoffice.system.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
public class UserServiceImpl extends BaseServiceImpl<UserMapper, User, UserVO> implements IUserService {

    private static final String PASSWORD_PATTERN = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^\\dA-Za-z]).{8,20}$";
    private static final String PHONE_PATTERN = "^1[3-9]\\d{9}$";
    private static final String EMAIL_PATTERN = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
    private static final String TELEPHONE_PATTERN = "^(?:\\d{3,4}-?)?\\d{7,8}$";
    private static final String ID_CARD_PATTERN = "(^\\d{15}$)|(^\\d{17}[\\dXx]$)";

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
    private DepartRoleMapper departRoleMapper;

    @Autowired
    private DepartRolePermissionMapper departRolePermissionMapper;

    @Autowired
    private DepartRoleUserMapper departRoleUserMapper;

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

        normalizeUser(user);

        boolean isCreate = !StringUtils.hasText(user.getId());
        validateUser(user, isCreate);
        
        if (isCreate) {
            log.info("新增用户，用户名: {}", user.getUsername());

            validateUnique(user, null);
            user.setPassword(passwordEncoder.encode(user.getPassword()));
            
            if (user.getStatus() == null) {
                user.setStatus(1);
            }
        } else {
            log.info("修改用户，用户ID: {}", user.getId());

            keepProtectedFields(user);
            validateUnique(user, user.getId());
        }
    }

    private void keepProtectedFields(User user) {
        User existing = userMapper.selectById(user.getId());
        if (existing == null || (existing.getDeleteFlag() != null && existing.getDeleteFlag() == 1)) {
            throw new IllegalArgumentException("用户不存在或已被删除");
        }

        user.setUsername(existing.getUsername());
        user.setPassword(null);
    }

    private void normalizeUser(User user) {
        user.setUsername(trimToNull(user.getUsername()));
        user.setRealname(trimToNull(user.getRealname()));
        user.setPassword(trimToNull(user.getPassword()));
        user.setEmail(trimToNull(user.getEmail()));
        user.setPhone(trimToNull(user.getPhone()));
        user.setWorkNo(trimToNull(user.getWorkNo()));
        user.setPost(trimToNull(user.getPost()));
        user.setTelephone(trimToNull(user.getTelephone()));
        user.setIdCard(trimToNull(user.getIdCard()));
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private void validateUser(User user, boolean isCreate) {
        if (!StringUtils.hasText(user.getUsername())) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (!StringUtils.hasText(user.getRealname())) {
            throw new IllegalArgumentException("真实姓名不能为空");
        }
        if (isCreate && !StringUtils.hasText(user.getPassword())) {
            throw new IllegalArgumentException("登录密码不能为空");
        }
        if (StringUtils.hasText(user.getPassword()) && !user.getPassword().matches(PASSWORD_PATTERN)) {
            throw new IllegalArgumentException("密码需为8-20位，包含大小写字母、数字和特殊字符");
        }
        if (StringUtils.hasText(user.getPhone()) && !user.getPhone().matches(PHONE_PATTERN)) {
            throw new IllegalArgumentException("手机号码格式不正确");
        }
        if (StringUtils.hasText(user.getEmail()) && !user.getEmail().matches(EMAIL_PATTERN)) {
            throw new IllegalArgumentException("邮箱格式不正确");
        }
        if (StringUtils.hasText(user.getTelephone()) && !user.getTelephone().matches(TELEPHONE_PATTERN)) {
            throw new IllegalArgumentException("座机号码格式不正确");
        }
        if (StringUtils.hasText(user.getIdCard()) && !user.getIdCard().matches(ID_CARD_PATTERN)) {
            throw new IllegalArgumentException("身份证号格式不正确");
        }
        if (user.getSex() != null && user.getSex() != 0 && user.getSex() != 1 && user.getSex() != 2) {
            throw new IllegalArgumentException("性别参数不正确");
        }
        if (user.getStatus() != null && user.getStatus() != 1 && user.getStatus() != 2) {
            throw new IllegalArgumentException("状态参数不正确");
        }
    }

    private void validateUnique(User user, String excludeId) {
        validateUniqueField(User::getUsername, user.getUsername(), excludeId, "用户名已存在");
        validateUniqueField(User::getPhone, user.getPhone(), excludeId, "手机号已被使用");
        validateUniqueField(User::getEmail, user.getEmail(), excludeId, "邮箱已被使用");
        validateUniqueField(User::getWorkNo, user.getWorkNo(), excludeId, "工号已被使用");
        validateUniqueField(User::getIdCard, user.getIdCard(), excludeId, "身份证号已被使用");
    }

    private void validateUniqueField(
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<User, ?> column,
            String value,
            String excludeId,
            String message) {
        if (!StringUtils.hasText(value)) {
            return;
        }

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(column, value)
               .eq(User::getDeleteFlag, 0);
        if (StringUtils.hasText(excludeId)) {
            wrapper.ne(User::getId, excludeId);
        }
        if (baseMapper.selectCount(wrapper) > 0) {
            throw new IllegalArgumentException(message);
        }
    }

    @Override
    protected void doAfterSave(BaseDTO<User> saveDTO, UserVO vo) {
        log.info("保存用户成功，用户ID: {}", vo.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    protected void doAfterDelete(BaseDTO<User> deleteDTO) {
        List<String> userIds = getDeleteIds(deleteDTO);
        if (userIds.isEmpty()) {
            return;
        }

        userIds.forEach(this::forceLogoutUserById);

        LambdaQueryWrapper<UserRole> userRoleWrapper = new LambdaQueryWrapper<>();
        userRoleWrapper.in(UserRole::getUserId, userIds);
        userRoleMapper.delete(userRoleWrapper);

        LambdaQueryWrapper<UserDepart> userDepartWrapper = new LambdaQueryWrapper<>();
        userDepartWrapper.in(UserDepart::getUserId, userIds);
        userDepartMapper.delete(userDepartWrapper);

        LambdaQueryWrapper<DepartRoleUser> departRoleUserWrapper = new LambdaQueryWrapper<>();
        departRoleUserWrapper.in(DepartRoleUser::getUserId, userIds);
        departRoleUserMapper.delete(departRoleUserWrapper);

        LambdaQueryWrapper<UserTenant> userTenantWrapper = new LambdaQueryWrapper<>();
        userTenantWrapper.in(UserTenant::getUserId, userIds);
        userTenantMapper.delete(userTenantWrapper);
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
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        User user = userMapper.selectById(userId);
        if (user == null || (user.getDeleteFlag() != null && user.getDeleteFlag() == 1)) {
            throw new IllegalArgumentException("用户不存在或已被删除");
        }

        List<String> normalizedRoleIds = normalizeIds(roleIds);
        validateRoles(normalizedRoleIds);

        // 先删除用户现有的所有角色
        LambdaQueryWrapper<UserRole> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(UserRole::getUserId, userId);
        userRoleMapper.delete(deleteWrapper);

        // 批量插入新的角色关联
        if (!normalizedRoleIds.isEmpty()) {
            List<UserRole> userRoles = normalizedRoleIds.stream()
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

        log.info("为用户分配角色成功，用户ID: {}, 角色数量: {}", userId, normalizedRoleIds.size());
        forceLogoutUserById(userId);
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
        roleWrapper.in(Role::getId, roleIds)
                   .eq(Role::getDeleteFlag, 0);
        return roleMapper.selectList(roleWrapper);
    }

    @Override
    public List<String> getUserRoleIds(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        LambdaQueryWrapper<UserRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserRole::getUserId, userId);
        return userRoleMapper.selectList(wrapper).stream()
                .map(UserRole::getRoleId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
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
        forceLogoutUserById(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignDeparts(String userId, List<String> departIds) {
        List<String> normalizedDepartIds = normalizeIds(departIds);

        // 先删除用户现有的所有部门
        LambdaQueryWrapper<UserDepart> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(UserDepart::getUserId, userId);
        userDepartMapper.delete(deleteWrapper);

        LambdaQueryWrapper<DepartRoleUser> deleteRoleUserWrapper = new LambdaQueryWrapper<>();
        deleteRoleUserWrapper.eq(DepartRoleUser::getUserId, userId);
        departRoleUserMapper.delete(deleteRoleUserWrapper);

        // 批量插入新的部门关联
        if (!normalizedDepartIds.isEmpty()) {
            List<UserDepart> userDeparts = normalizedDepartIds.stream()
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

            assignDefaultDepartRoles(userId, normalizedDepartIds);
        }

        log.info("为用户分配部门成功，用户ID: {}, 部门数量: {}", userId, departIds == null ? 0 : departIds.size());
        forceLogoutUserById(userId);
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
        List<String> normalizedDepartIds = normalizeIds(departIds);
        if (normalizedDepartIds.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<UserDepart> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDepart::getUserId, userId)
               .in(UserDepart::getDepId, normalizedDepartIds);
        userDepartMapper.delete(wrapper);

        List<String> departRoleIds = getDepartRoleIds(normalizedDepartIds);
        if (!departRoleIds.isEmpty()) {
            LambdaQueryWrapper<DepartRoleUser> roleUserWrapper = new LambdaQueryWrapper<>();
            roleUserWrapper.eq(DepartRoleUser::getUserId, userId)
                    .in(DepartRoleUser::getDroleId, departRoleIds);
            departRoleUserMapper.delete(roleUserWrapper);
        }

        log.info("移除用户部门成功，用户ID: {}, 移除部门数量: {}", userId, departIds.size());
        forceLogoutUserById(userId);
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
        List<Permission> departRolePermissions = getUserDepartRolePermissions(userId);

        if (userRoles.isEmpty()) {
            return departRolePermissions;
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
            return departRolePermissions;
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
        return mergePermissions(permissionMapper.selectList(permWrapper), departRolePermissions);
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
        if (user.getStatus() == null || user.getStatus() != 1) {
            log.warn("登录失败：用户已被禁用 - {}", username);
            throw new RuntimeException("用户已被禁用，请联系管理员");
        }
        
        // 3. 验证密码
        if (!verifyPassword(password, user.getPassword())) {
            log.warn("登录失败：密码错误 - {}", username);
            throw new RuntimeException("用户名或密码错误");
        }
        
        // 4. 获取用户的默认租户ID（从UserTenant关系中获取第一个正常状态的租户）
        String defaultTenantId = getDefaultTenantId(user.getId());
        log.info("用户 {} 的默认租户ID: {}", username, defaultTenantId);
        
        // 5. 获取用户权限列表
        List<String> permissionCodes = getUserPermissionCodes(user.getId());
        log.info("用户 {} 的权限列表: {}", username, permissionCodes);
        
        // 6. 获取用户角色列表
        List<String> roleCodes = getUserRoleCodes(user.getId());
        log.info("用户 {} 的角色列表: {}", username, roleCodes);
        
        // 7. 强制清除所有旧的 Redis 缓存（包括 Token、权限、角色）
        tokenService.forceLogout(username);
        
        // 8. 生成并存储Token到Redis（传入租户ID）
        String token = tokenService.generateAndStoreTokenWithTenant(
                username,
                user.getId(),
                user.getRealname(),
                permissionCodes,
                roleCodes,
                defaultTenantId
        );
        
        // 9. 构建返回结果（只返回token）
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("token", token);
        result.put("tenantId", defaultTenantId);  // 返回租户ID给前端
        
        log.info("用户登录成功：{}, 租户ID: {}", username, defaultTenantId);
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
    public UserInfoVO getCurrentUserDetailInfo(String username) {
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
        List<String> roleCodes = getUserRoleCodes(user.getId());
        log.info("用户 {} 的角色列表: {}", username, roleCodes);
        
        // 4. 构建返回结果
        UserInfoVO userInfoVO = new UserInfoVO();
        userInfoVO.setUserId(user.getId());
        userInfoVO.setUsername(user.getUsername());
        userInfoVO.setRealName(user.getRealname());
        userInfoVO.setPermissions(permissionCodes);
        userInfoVO.setRoles(roleCodes);
        // homePath 可以根据业务需求设置
        userInfoVO.setHomePath("/system/user");
        
        log.info("获取用户详细信息成功：{}, 权限数量: {}, 角色数量: {}", username, permissionCodes.size(), roleCodes.size());
        return userInfoVO;
    }

    private void assignDefaultDepartRoles(String userId, List<String> departIds) {
        List<String> defaultRoleIds = getDefaultDepartRoleIds(departIds);
        if (defaultRoleIds.isEmpty()) {
            return;
        }

        for (String roleId : defaultRoleIds) {
            DepartRoleUser roleUser = new DepartRoleUser();
            roleUser.setUserId(userId);
            roleUser.setDroleId(roleId);
            departRoleUserMapper.insert(roleUser);
        }
    }

    private List<Permission> getUserDepartRolePermissions(String userId) {
        List<String> departRoleIds = getUserDepartRoleIds(userId);
        if (departRoleIds.isEmpty()) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<DepartRolePermission> rolePermissionWrapper = new LambdaQueryWrapper<>();
        rolePermissionWrapper.in(DepartRolePermission::getRoleId, departRoleIds);
        List<String> permissionIds = departRolePermissionMapper.selectList(rolePermissionWrapper).stream()
                .map(DepartRolePermission::getPermissionId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());

        if (permissionIds.isEmpty()) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<Permission> permissionWrapper = new LambdaQueryWrapper<>();
        permissionWrapper.in(Permission::getId, permissionIds)
                .eq(Permission::getStatus, "1")
                .orderByAsc(Permission::getSortNo);
        return permissionMapper.selectList(permissionWrapper);
    }

    private List<String> getUserRoleCodes(String userId) {
        Set<String> roleCodes = new LinkedHashSet<>();
        getUserRoles(userId).stream()
                .map(Role::getRoleCode)
                .filter(StringUtils::hasText)
                .forEach(roleCodes::add);

        List<String> departRoleIds = getUserDepartRoleIds(userId);
        if (!departRoleIds.isEmpty()) {
            LambdaQueryWrapper<DepartRole> wrapper = new LambdaQueryWrapper<>();
            wrapper.in(DepartRole::getId, departRoleIds)
                    .eq(DepartRole::getDeleteFlag, 0);
            departRoleMapper.selectList(wrapper).stream()
                    .map(DepartRole::getRoleCode)
                    .filter(StringUtils::hasText)
                    .forEach(roleCodes::add);
        }

        return new ArrayList<>(roleCodes);
    }

    private List<String> getUserDepartRoleIds(String userId) {
        if (!StringUtils.hasText(userId)) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<DepartRoleUser> roleUserWrapper = new LambdaQueryWrapper<>();
        roleUserWrapper.eq(DepartRoleUser::getUserId, userId);
        List<String> roleIds = departRoleUserMapper.selectList(roleUserWrapper).stream()
                .map(DepartRoleUser::getDroleId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());

        if (roleIds.isEmpty()) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<DepartRole> roleWrapper = new LambdaQueryWrapper<>();
        roleWrapper.in(DepartRole::getId, roleIds)
                .eq(DepartRole::getDeleteFlag, 0);
        return departRoleMapper.selectList(roleWrapper).stream()
                .map(DepartRole::getId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<String> getDefaultDepartRoleIds(List<String> departIds) {
        List<String> normalizedDepartIds = normalizeIds(departIds);
        if (normalizedDepartIds.isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> departIdSet = new LinkedHashSet<>(normalizedDepartIds);
        LambdaQueryWrapper<DepartRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(DepartRole::getDepartId, normalizedDepartIds)
                .eq(DepartRole::getDeleteFlag, 0);
        return departRoleMapper.selectList(wrapper).stream()
                .filter(role -> StringUtils.hasText(role.getDepartId()))
                .filter(role -> departIdSet.contains(role.getDepartId()))
                .filter(role -> role.getDepartId().equals(role.getRoleCode()))
                .map(DepartRole::getId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<String> getDepartRoleIds(List<String> departIds) {
        List<String> normalizedDepartIds = normalizeIds(departIds);
        if (normalizedDepartIds.isEmpty()) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<DepartRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(DepartRole::getDepartId, normalizedDepartIds);
        return departRoleMapper.selectList(wrapper).stream()
                .map(DepartRole::getId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<Permission> mergePermissions(List<Permission> rolePermissions, List<Permission> departRolePermissions) {
        Set<String> permissionIds = new LinkedHashSet<>();
        List<Permission> permissions = new ArrayList<>();
        for (Permission permission : rolePermissions) {
            if (permission != null && StringUtils.hasText(permission.getId()) && permissionIds.add(permission.getId())) {
                permissions.add(permission);
            }
        }
        for (Permission permission : departRolePermissions) {
            if (permission != null && StringUtils.hasText(permission.getId()) && permissionIds.add(permission.getId())) {
                permissions.add(permission);
            }
        }
        return permissions;
    }

    /**
     * 获取用户的默认租户ID
     * 从UserTenant关系中获取第一个正常状态的租户
     */
    private String getDefaultTenantId(String userId) {
        LambdaQueryWrapper<UserTenant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserTenant::getUserId, userId)
               .eq(UserTenant::getStatus, "1")  // 状态为正常
               .orderByAsc(UserTenant::getCreateTime)
               .last("LIMIT 1");
        
        UserTenant userTenant = userTenantMapper.selectOne(wrapper);
        
        if (userTenant != null) {
            return userTenant.getTenantId();
        }
        
        // 如果没有关联的租户，返回默认租户ID "0"
        return "0";
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

    private void validateRoles(List<String> roleIds) {
        if (roleIds.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Role::getId, roleIds)
               .eq(Role::getDeleteFlag, 0);
        long count = roleMapper.selectCount(wrapper);
        if (count != roleIds.size()) {
            throw new IllegalArgumentException("包含不存在或已删除的角色");
        }
    }

    private List<String> getDeleteIds(BaseDTO<User> deleteDTO) {
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

    private void forceLogoutUserById(String userId) {
        if (!StringUtils.hasText(userId)) {
            return;
        }

        User user = userMapper.selectById(userId);
        if (user != null && StringUtils.hasText(user.getUsername())) {
            tokenService.forceLogout(user.getUsername());
        }
    }
}
