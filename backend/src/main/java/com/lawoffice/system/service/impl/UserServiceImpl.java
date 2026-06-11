package com.lawoffice.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lawoffice.framework.config.TenantContextHolder;
import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.framework.dto.BasePageDTO;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.entity.SysLog;
import com.lawoffice.framework.mapper.LogMapper;
import com.lawoffice.framework.req.BasePageReq;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.framework.util.QueryWrapperBuilderUtils;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.system.constant.DepartRoleCodes;
import com.lawoffice.system.constant.PermissionMenuTypes;
import com.lawoffice.system.req.CurrentUserProfileReq;
import com.lawoffice.system.req.FileUploadReq;
import com.lawoffice.system.vo.CurrentUserLogVO;
import com.lawoffice.system.vo.CurrentUserOrganizationVO;
import com.lawoffice.system.vo.CurrentUserPermissionSummaryVO;
import com.lawoffice.system.vo.CurrentUserProfileVO;
import com.lawoffice.system.vo.CurrentUserTenantVO;
import com.lawoffice.system.vo.DepartRoleVO;
import com.lawoffice.system.vo.RoleVO;
import com.lawoffice.system.vo.SysDepartVO;
import com.lawoffice.system.vo.UserInfoVO;
import com.lawoffice.system.entity.*;
import com.lawoffice.system.mapper.*;
import com.lawoffice.system.service.IUserService;
import com.lawoffice.system.service.ISysFilesService;
import com.lawoffice.system.vo.UserVO;
import com.lawoffice.util.EntityFillUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserServiceImpl extends BaseServiceImpl<UserMapper, User, UserVO> implements IUserService {

    private static final String PLATFORM_ADMIN_ROLE_CODE = "ADMIN";
    private static final String SYSTEM_TENANT_ID = "0";
    private static final String TENANT_ADMIN_ROLE_CODE_PREFIX = "ADMIN_";
    private static final String DEFAULT_HOME_PATH = "/analytics";
    private static final String PASSWORD_PATTERN = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^\\dA-Za-z]).{8,20}$";
    private static final String PHONE_PATTERN = "^1[3-9]\\d{9}$";
    private static final String EMAIL_PATTERN = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";
    private static final String TELEPHONE_PATTERN = "^(?:\\d{3,4}-?)?\\d{7,8}$";
    private static final String ID_CARD_PATTERN = "(^\\d{15}$)|(^\\d{17}[\\dXx]$)";
    private static final Set<String> CURRENT_USER_LOG_QUERY_FIELDS = Set.of(
            "logType", "logContent", "operateType", "ip", "requestUrl",
            "requestType", "costTime", "clientType", "createTime"
    );

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
    private LogMapper logMapper;

    @Autowired
    private ISysFilesService sysFilesService;
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private com.lawoffice.system.service.ITokenService tokenService;
    
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    protected void doBeforeList(BaseDTO<User> baseDTO) {
        applyTenantMemberScope(baseDTO);
    }

    @Override
    protected void doBeforePage(BasePageDTO<User> basePageDTO) {
        applyTenantMemberScope(basePageDTO);
    }

    @Override
    protected void doBeforeGetById(BaseDTO<User> idDTO) {
        if (isPlatformOperator(idDTO)) {
            return;
        }
        String tenantId = getRequiredTenantId(idDTO);
        if (!userBelongsToTenant(idDTO.getId(), tenantId)) {
            throw new IllegalArgumentException("只能查看当前租户成员");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<UserVO> save(BaseDTO<User> saveDTO) {
        if (isPlatformOperator(saveDTO)) {
            return super.save(saveDTO);
        }

        try {
            User requestUser = saveDTO.getEntity();
            if (requestUser == null) {
                return BaseResult.error(400, "保存数据不能为空");
            }

            normalizeUser(requestUser);
            String tenantId = getRequiredTenantId(saveDTO);
            boolean isCreate = !StringUtils.hasText(requestUser.getId());

            User entity;
            if (isCreate) {
                entity = saveOrJoinTenantUser(saveDTO, requestUser, tenantId);
            } else {
                if (!userBelongsToTenant(requestUser.getId(), tenantId)) {
                    throw new IllegalArgumentException("只能编辑当前租户成员");
                }
                validateUser(requestUser, false);
                keepProtectedFields(requestUser);
                validateUnique(requestUser, requestUser.getId());
                entity = BeanUtil.copyProperties(requestUser, User.class);
                EntityFillUtils.fillAuditFields(entity, saveDTO.getContext(), false);
                this.saveOrUpdate(entity);
            }

            UserVO vo = BeanUtil.toBean(entity, UserVO.class);
            doAfterSave(saveDTO, vo);
            return BaseResult.success(vo);
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
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<Void> delete(BaseDTO<User> deleteDTO) {
        if (isPlatformOperator(deleteDTO)) {
            return super.delete(deleteDTO);
        }

        try {
            String userId = deleteDTO.getId();
            if (!StringUtils.hasText(userId)) {
                return BaseResult.error("ID不能为空");
            }
            removeUsersFromCurrentTenant(List.of(userId), deleteDTO);
            return BaseResult.success();
        } catch (Exception e) {
            markRollbackOnly();
            log.error("移出租户成员失败", e);
            return BaseResult.error("删除失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<Void> batchDelete(BaseDTO<User> deleteDTO) {
        if (isPlatformOperator(deleteDTO)) {
            return super.batchDelete(deleteDTO);
        }

        try {
            List<String> userIds = normalizeIds(deleteDTO.getDeleteIds());
            if (userIds.isEmpty()) {
                return BaseResult.error("删除ID列表不能为空");
            }
            removeUsersFromCurrentTenant(userIds, deleteDTO);
            return BaseResult.success();
        } catch (Exception e) {
            markRollbackOnly();
            log.error("批量移出租户成员失败", e);
            return BaseResult.error("批量删除失败: " + e.getMessage());
        }
    }

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

    /**
     * 编辑用户时保留不允许由普通保存接口覆盖的敏感字段。
     */
    private void keepProtectedFields(User user) {
        User existing = userMapper.selectById(user.getId());
        if (existing == null || (existing.getDeleteFlag() != null && existing.getDeleteFlag() == 1)) {
            throw new IllegalArgumentException("用户不存在或已被删除");
        }

        user.setUsername(existing.getUsername());
        user.setPassword(null);
    }

    /**
     * 统一清理用户输入中的空白字符串，避免唯一性校验和保存时出现空串脏数据。
     */
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

    /**
     * 将字符串 trim 后的空值统一转为 null。
     */
    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    /**
     * 校验用户基础字段和新增时必须提供的密码。
     */
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

    /**
     * 校验用户名、手机号、邮箱、工号和身份证号的全局唯一性。
     */
    private void validateUnique(User user, String excludeId) {
        validateUniqueField(User::getUsername, user.getUsername(), excludeId, "用户名已存在");
        validateUniqueField(User::getPhone, user.getPhone(), excludeId, "手机号已被使用");
        validateUniqueField(User::getEmail, user.getEmail(), excludeId, "邮箱已被使用");
        validateUniqueField(User::getWorkNo, user.getWorkNo(), excludeId, "工号已被使用");
        validateUniqueField(User::getIdCard, user.getIdCard(), excludeId, "身份证号已被使用");
    }

    /**
     * 按单个字段校验用户唯一性，并排除当前编辑用户。
     */
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

    /**
     * 个人中心只允许当前用户维护联系方式和展示信息，仍复用用户全局唯一约束。
     */
    private void validateCurrentUserProfileUnique(String userId, CurrentUserProfileReq req) {
        validateUniqueField(User::getPhone, req.getPhone(), userId, "手机号已被使用");
        validateUniqueField(User::getEmail, req.getEmail(), userId, "邮箱已被使用");
    }

    /**
     * 非超级管理员查询用户时，将结果限制在当前租户成员范围内。
     */
    private void applyTenantMemberScope(BaseDTO<User> baseDTO) {
        if (isPlatformOperator(baseDTO)) {
            return;
        }

        String tenantId = getRequiredTenantId(baseDTO);
        List<String> memberUserIds = getTenantMemberUserIds(tenantId);
        QueryWrapper<User> wrapper = (QueryWrapper<User>) baseDTO.getQueryWrapper();
        if (wrapper == null) {
            wrapper = new QueryWrapper<>();
            baseDTO.setQueryWrapper(wrapper);
        }

        if (memberUserIds.isEmpty()) {
            wrapper.eq("id", "__none__");
            return;
        }
        wrapper.in("id", memberUserIds);
    }

    /**
     * 非超级管理员新增用户时，优先复用已存在的全局账号并加入当前租户。
     */
    private User saveOrJoinTenantUser(BaseDTO<User> saveDTO, User requestUser, String tenantId) {
        User existingUser = findExistingUserForTenantJoin(requestUser);
        if (existingUser != null) {
            // 多租户下用户主账号全局唯一：命中已有正常账号时，只补齐当前租户成员关系，不覆盖账号资料。
            ensureJoinableUser(existingUser);
            upsertUserTenantRelation(existingUser.getId(), tenantId);
            log.info("已有用户加入当前租户成功，用户ID: {}, 租户ID: {}", existingUser.getId(), tenantId);
            return existingUser;
        }

        validateUser(requestUser, true);
        validateUnique(requestUser, null);
        requestUser.setPassword(passwordEncoder.encode(requestUser.getPassword()));
        if (requestUser.getStatus() == null) {
            requestUser.setStatus(1);
        }

        User entity = BeanUtil.copyProperties(requestUser, User.class);
        if (!StringUtils.hasText(entity.getId())) {
            entity.setId(newId());
        }
        RequestContext context = saveDTO.getContext();
        EntityFillUtils.fillAuditFields(entity, context, true);
        this.saveOrUpdate(entity);
        upsertUserTenantRelation(entity.getId(), tenantId);
        return entity;
    }

    /**
     * 按用户名、手机号或邮箱查找可加入当前租户的已有账号。
     */
    private User findExistingUserForTenantJoin(User user) {
        List<User> matchedUsers = new ArrayList<>();
        addMatchedUsers(matchedUsers, User::getUsername, user.getUsername());
        addMatchedUsers(matchedUsers, User::getPhone, user.getPhone());
        addMatchedUsers(matchedUsers, User::getEmail, user.getEmail());

        Map<String, User> userMap = matchedUsers.stream()
                .filter(matchedUser -> StringUtils.hasText(matchedUser.getId()))
                .collect(Collectors.toMap(User::getId, matchedUser -> matchedUser, (left, right) -> left));
        if (userMap.isEmpty()) {
            return null;
        }
        if (userMap.size() > 1) {
            throw new IllegalArgumentException("用户名、手机号或邮箱匹配到多个账号，请使用唯一账号信息加入租户");
        }
        return userMap.values().iterator().next();
    }

    /**
     * 按指定字段追加匹配到的已有用户，用于判断是否复用全局账号加入租户。
     */
    private void addMatchedUsers(
            List<User> matchedUsers,
            com.baomidou.mybatisplus.core.toolkit.support.SFunction<User, ?> column,
            String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(column, value);
        matchedUsers.addAll(userMapper.selectList(wrapper));
    }

    /**
     * 确认已有账号处于可加入新租户的正常状态。
     */
    private void ensureJoinableUser(User user) {
        if (user.getDeleteFlag() != null && user.getDeleteFlag() == 1) {
            throw new IllegalArgumentException("账号已被删除，不能加入当前租户");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new IllegalArgumentException("账号已被冻结，不能加入当前租户");
        }
    }

    /**
     * 非超级管理员删除用户时，仅移除当前租户成员关系和当前租户角色关系。
     */
    private void removeUsersFromCurrentTenant(List<String> userIds, BaseDTO<User> deleteDTO) {
        String tenantId = getRequiredTenantId(deleteDTO);
        String deleteBy = resolveOperator(deleteDTO);

        // 非超级管理员删除用户时只移出当前租户，不能删除全局用户主账号或影响其他租户关系。
        for (String userId : userIds) {
            if (!userBelongsToTenant(userId, tenantId)) {
                throw new IllegalArgumentException("只能移出当前租户成员");
            }
        }

        LambdaQueryWrapper<UserTenant> userTenantWrapper = new LambdaQueryWrapper<>();
        userTenantWrapper.in(UserTenant::getUserId, userIds)
                .eq(UserTenant::getTenantId, tenantId)
                .eq(UserTenant::getDeleteFlag, 0);
        softDeleteUserTenants(userTenantWrapper, deleteBy);

        LambdaQueryWrapper<UserRole> userRoleWrapper = new LambdaQueryWrapper<>();
        userRoleWrapper.in(UserRole::getUserId, userIds)
                .eq(UserRole::getTenantId, tenantId)
                .eq(UserRole::getDeleteFlag, 0);
        softDeleteUserRoles(userRoleWrapper, deleteBy);

        userIds.forEach(this::forceLogoutUserById);
        log.info("移出当前租户成员成功，租户ID: {}, 用户数量: {}", tenantId, userIds.size());
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
        String deleteBy = resolveOperator(deleteDTO);

        LambdaQueryWrapper<UserRole> userRoleWrapper = new LambdaQueryWrapper<>();
        userRoleWrapper.in(UserRole::getUserId, userIds)
                .eq(UserRole::getDeleteFlag, 0);
        softDeleteUserRoles(userRoleWrapper, deleteBy);

        LambdaQueryWrapper<UserDepart> userDepartWrapper = new LambdaQueryWrapper<>();
        userDepartWrapper.in(UserDepart::getUserId, userIds)
                .eq(UserDepart::getDeleteFlag, 0);
        softDeleteUserDeparts(userDepartWrapper, deleteBy);

        LambdaQueryWrapper<DepartRoleUser> departRoleUserWrapper = new LambdaQueryWrapper<>();
        departRoleUserWrapper.in(DepartRoleUser::getUserId, userIds)
                .eq(DepartRoleUser::getDeleteFlag, 0);
        softDeleteDepartRoleUsers(departRoleUserWrapper, deleteBy);

        LambdaQueryWrapper<UserTenant> userTenantWrapper = new LambdaQueryWrapper<>();
        userTenantWrapper.in(UserTenant::getUserId, userIds)
                .eq(UserTenant::getDeleteFlag, 0);
        softDeleteUserTenants(userTenantWrapper, deleteBy);
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
        assignRoles(userId, roleIds, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(String userId, List<String> roleIds, String operatorUsername) {
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        User user = userMapper.selectById(userId);
        if (user == null || (user.getDeleteFlag() != null && user.getDeleteFlag() == 1)) {
            throw new IllegalArgumentException("用户不存在或已被删除");
        }

        List<String> normalizedRoleIds = normalizeIds(roleIds);
        validateRoles(normalizedRoleIds);
        boolean platformOperator = isPlatformOperatorUsername(operatorUsername);
        // 租户管理员只能给当前租户成员分配当前租户角色，且被分配角色的权限不能超过自身权限。
        validateTenantRoleAssignment(userId, normalizedRoleIds, operatorUsername, platformOperator);

        // 先删除用户现有的所有角色
        LambdaQueryWrapper<UserRole> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(UserRole::getUserId, userId)
                .eq(UserRole::getDeleteFlag, 0);
        if (!platformOperator) {
            deleteWrapper.eq(UserRole::getTenantId, getRequiredTenantId());
        }
        userRoleMapper.delete(deleteWrapper);

        // 批量插入新的角色关联
        if (!normalizedRoleIds.isEmpty()) {
            String currentTenantId = !platformOperator ? getRequiredTenantId() : null;
            List<UserRole> userRoles = normalizedRoleIds.stream()
                .map(roleId -> {
                    UserRole userRole = new UserRole();
                    userRole.setUserId(userId);
                    userRole.setRoleId(roleId);
                    if (StringUtils.hasText(currentTenantId)) {
                        userRole.setTenantId(currentTenantId);
                    }
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
        wrapper.eq(UserRole::getUserId, userId)
               .eq(UserRole::getDeleteFlag, 0);
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
        wrapper.eq(UserRole::getUserId, userId)
               .eq(UserRole::getDeleteFlag, 0);
        String tenantId = TenantContextHolder.getCurrentTenantId();
        if (StringUtils.hasText(tenantId)) {
            wrapper.eq(UserRole::getTenantId, tenantId);
        }
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
               .in(UserRole::getRoleId, roleIds)
               .eq(UserRole::getDeleteFlag, 0);
        userRoleMapper.delete(wrapper);

        log.info("移除用户角色成功，用户ID: {}, 移除角色数量: {}", userId, roleIds.size());
        forceLogoutUserById(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignDeparts(String userId, List<String> departIds) {
        List<String> normalizedDepartIds = normalizeIds(departIds);
        String deleteBy = "system";

        // 先删除用户现有的所有部门
        LambdaQueryWrapper<UserDepart> deleteWrapper = new LambdaQueryWrapper<>();
        deleteWrapper.eq(UserDepart::getUserId, userId)
                .eq(UserDepart::getDeleteFlag, 0);
        softDeleteUserDeparts(deleteWrapper, deleteBy);

        LambdaQueryWrapper<DepartRoleUser> deleteRoleUserWrapper = new LambdaQueryWrapper<>();
        deleteRoleUserWrapper.eq(DepartRoleUser::getUserId, userId)
                .eq(DepartRoleUser::getDeleteFlag, 0);
        softDeleteDepartRoleUsers(deleteRoleUserWrapper, deleteBy);

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
        wrapper.eq(UserDepart::getUserId, userId)
                .eq(UserDepart::getDeleteFlag, 0);
        String tenantId = TenantContextHolder.getCurrentTenantId();
        if (StringUtils.hasText(tenantId)) {
            wrapper.eq(UserDepart::getTenantId, tenantId);
        }
        List<UserDepart> userDeparts = userDepartMapper.selectList(wrapper);

        if (userDeparts.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> departIds = userDeparts.stream()
            .map(UserDepart::getDepId)
            .filter(StringUtils::hasText)
            .collect(Collectors.toList());
        List<String> visibleDepartIds = collectDepartIdsWithAncestors(departIds, tenantId);
        if (visibleDepartIds.isEmpty()) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<SysDepart> departWrapper = new LambdaQueryWrapper<>();
        departWrapper.in(SysDepart::getId, visibleDepartIds)
                .eq(SysDepart::getDeleteFlag, 0);
        if (StringUtils.hasText(tenantId)) {
            departWrapper.eq(SysDepart::getTenantId, tenantId);
        }
        List<SysDepart> departs = sysDepartMapper.selectList(departWrapper);
        Map<String, SysDepart> departMap = departs.stream()
                .filter(item -> StringUtils.hasText(item.getId()))
                .collect(Collectors.toMap(SysDepart::getId, item -> item, (left, right) -> left));
        return visibleDepartIds.stream()
                .map(departMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<String> collectDepartIdsWithAncestors(List<String> departIds, String tenantId) {
        if (departIds == null || departIds.isEmpty()) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<SysDepart> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(SysDepart::getId, SysDepart::getParentId)
                .eq(SysDepart::getDeleteFlag, 0);
        if (StringUtils.hasText(tenantId)) {
            wrapper.eq(SysDepart::getTenantId, tenantId);
        }
        Map<String, String> parentIdByDepartId = new HashMap<>();
        for (SysDepart depart : sysDepartMapper.selectList(wrapper)) {
            if (StringUtils.hasText(depart.getId())) {
                parentIdByDepartId.put(depart.getId(), depart.getParentId());
            }
        }
        LinkedHashSet<String> visibleIds = new LinkedHashSet<>();
        for (String departId : departIds) {
            String currentId = departId;
            int guard = 0;
            while (StringUtils.hasText(currentId) && guard++ < 20 && visibleIds.add(currentId)) {
                currentId = parentIdByDepartId.get(currentId);
            }
        }
        return new ArrayList<>(visibleIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeDeparts(String userId, List<String> departIds) {
        List<String> normalizedDepartIds = normalizeIds(departIds);
        if (normalizedDepartIds.isEmpty()) {
            return;
        }
        String deleteBy = "system";

        LambdaQueryWrapper<UserDepart> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserDepart::getUserId, userId)
               .in(UserDepart::getDepId, normalizedDepartIds)
               .eq(UserDepart::getDeleteFlag, 0);
        softDeleteUserDeparts(wrapper, deleteBy);

        List<String> departRoleIds = getDepartRoleIds(normalizedDepartIds);
        if (!departRoleIds.isEmpty()) {
            LambdaQueryWrapper<DepartRoleUser> roleUserWrapper = new LambdaQueryWrapper<>();
            roleUserWrapper.eq(DepartRoleUser::getUserId, userId)
                    .in(DepartRoleUser::getDroleId, departRoleIds)
                    .eq(DepartRoleUser::getDeleteFlag, 0);
            softDeleteDepartRoleUsers(roleUserWrapper, deleteBy);
        }

        log.info("移除用户部门成功，用户ID: {}, 移除部门数量: {}", userId, departIds.size());
        forceLogoutUserById(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignTenants(String userId, List<String> tenantIds) {
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("用户ID不能为空");
        }

        User user = getEnabledUserById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在或已被删除");
        }

        List<String> normalizedTenantIds = normalizeIds(tenantIds);
        validateTenants(normalizedTenantIds);

        syncUserTenants(userId, normalizedTenantIds);

        log.info("为用户分配租户成功，用户ID: {}, 租户数量: {}", userId, normalizedTenantIds.size());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignTenantUsers(String tenantId, List<String> userIds) {
        if (!StringUtils.hasText(tenantId)) {
            throw new IllegalArgumentException("租户ID不能为空");
        }

        Tenant tenant = getEnabledTenantById(tenantId);
        if (tenant == null) {
            throw new IllegalArgumentException("租户不存在或已冻结");
        }

        List<String> normalizedUserIds = normalizeIds(userIds);
        validateUsers(normalizedUserIds);
        syncTenantUsers(tenantId, normalizedUserIds);

        log.info("为租户分配用户成功，租户ID: {}, 用户数量: {}", tenantId, normalizedUserIds.size());
    }

    @Override
    public List<Tenant> getUserTenants(String userId) {
        // 查询用户的租户ID列表
        LambdaQueryWrapper<UserTenant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserTenant::getUserId, userId)
               .eq(UserTenant::getStatus, "1")
               .eq(UserTenant::getDeleteFlag, 0);
        List<UserTenant> userTenants = userTenantMapper.selectList(wrapper);

        if (userTenants.isEmpty()) {
            return new ArrayList<>();
        }

        // 根据租户ID查询租户详情
        List<String> tenantIds = userTenants.stream()
            .map(UserTenant::getTenantId)
            .collect(Collectors.toList());

        LambdaQueryWrapper<Tenant> tenantWrapper = new LambdaQueryWrapper<>();
        tenantWrapper.in(Tenant::getId, tenantIds)
                     .eq(Tenant::getDeleteFlag, 0);
        return tenantMapper.selectList(tenantWrapper);
    }

    @Override
    public List<String> getTenantUserIds(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            throw new IllegalArgumentException("租户ID不能为空");
        }

        LambdaQueryWrapper<UserTenant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserTenant::getTenantId, tenantId)
               .eq(UserTenant::getStatus, "1")
               .eq(UserTenant::getDeleteFlag, 0);
        return userTenantMapper.selectList(wrapper).stream()
                .map(UserTenant::getUserId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public List<Tenant> getCurrentUserTenants(String username) {
        User user = getCurrentUserInfo(username);
        if (isPlatformAdmin(user.getId())) {
            return getEnabledTenants();
        }
        return getUserTenants(user.getId()).stream()
                .filter(tenant -> tenant.getDeleteFlag() == null || tenant.getDeleteFlag() == 0)
                .filter(tenant -> tenant.getStatus() != null && tenant.getStatus() == 1)
                .collect(Collectors.toList());
    }

    @Override
    public List<UserVO> getCurrentTenantUsers(String username) {
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("未登录或登录已过期");
        }

        String tenantId = getRequiredTenantId();
        List<String> userIds = getTenantMemberUserIds(tenantId);
        if (userIds.isEmpty()) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(User::getId, userIds)
                .eq(User::getDeleteFlag, 0)
                .eq(User::getStatus, 1)
                .orderByAsc(User::getUsername);
        return BeanUtil.copyToList(userMapper.selectList(wrapper), UserVO.class);
    }

    @Override
    public List<UserVO> getCurrentTenantRoleUsers(String username, String roleId) {
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("未登录或登录已过期");
        }
        if (!StringUtils.hasText(roleId)) {
            throw new IllegalArgumentException("角色ID不能为空");
        }

        String tenantId = getRequiredTenantId();
        Role role = roleMapper.selectById(roleId);
        if (role == null
                || (role.getDeleteFlag() != null && role.getDeleteFlag() != 0)
                || !tenantId.equals(role.getTenantId())) {
            return new ArrayList<>();
        }

        List<String> tenantUserIds = getTenantMemberUserIds(tenantId);
        if (tenantUserIds.isEmpty()) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<UserRole> roleUserWrapper = new LambdaQueryWrapper<>();
        roleUserWrapper.eq(UserRole::getRoleId, roleId)
                .eq(UserRole::getTenantId, tenantId)
                .eq(UserRole::getDeleteFlag, 0)
                .in(UserRole::getUserId, tenantUserIds);
        List<String> roleUserIds = userRoleMapper.selectList(roleUserWrapper).stream()
                .map(UserRole::getUserId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (roleUserIds.isEmpty()) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(User::getId, roleUserIds)
                .eq(User::getDeleteFlag, 0)
                .eq(User::getStatus, 1)
                .orderByAsc(User::getUsername);
        return BeanUtil.copyToList(userMapper.selectList(wrapper), UserVO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeTenants(String userId, List<String> tenantIds) {
        if (tenantIds == null || tenantIds.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<UserTenant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserTenant::getUserId, userId)
               .in(UserTenant::getTenantId, tenantIds)
               .eq(UserTenant::getDeleteFlag, 0);
        softDeleteUserTenants(wrapper, "system");

        log.info("移除用户租户成功，用户ID: {}, 移除租户数量: {}", userId, tenantIds.size());
    }

    @Override
    public List<Permission> getUserPermissions(String userId) {
        // 1. 获取用户的角色ID列表
        LambdaQueryWrapper<UserRole> userRoleWrapper = new LambdaQueryWrapper<>();
        userRoleWrapper.eq(UserRole::getUserId, userId)
                .eq(UserRole::getDeleteFlag, 0);
        List<UserRole> userRoles = userRoleMapper.selectList(userRoleWrapper);
        List<String> departRoleIds = getUserDepartRoleIds(userId);

        List<String> roleIds = userRoles.stream()
            .map(UserRole::getRoleId)
            .distinct()
            .collect(Collectors.toList());
        return getPermissionsByRoleIds(roleIds, departRoleIds);
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
    public List<Permission> getUserPermissionsInCurrentTenant(String userId) {
        return getUserPermissionsInTenant(userId, TenantContextHolder.getCurrentTenantId());
    }

    @Override
    public List<String> getUserPermissionCodesByUsername(String username) {
        User user = getCurrentUserInfo(username);
        String currentTenantId = TenantContextHolder.getCurrentTenantId();
        return getUserPermissionCodesInTenant(user.getId(), currentTenantId);
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
        
        // 4. 获取用户的默认租户ID（优先使用上次选择租户）
        String defaultTenantId = getDefaultTenantId(user.getId());
        log.info("用户 {} 的默认租户ID: {}", username, defaultTenantId);
        
        // 5. 获取用户在默认租户下的权限列表
        List<String> permissionCodes = getUserPermissionCodesInTenant(user.getId(), defaultTenantId);
        log.info("用户 {} 的权限列表: {}", username, permissionCodes);
        
        // 6. 获取用户在默认租户下的角色列表
        List<String> roleCodes = getUserRoleCodesInTenant(user.getId(), defaultTenantId);
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
        user.setLoginTenantId(defaultTenantId);
        userMapper.updateById(user);

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("token", token);
        result.put("tenantId", defaultTenantId);  // 返回租户ID给前端
        
        log.info("用户登录成功：{}, 租户ID: {}", username, defaultTenantId);
        return result;
    }

    @Override
    public Map<String, Object> switchTenant(String username, String tenantId, String currentToken) {
        if (!StringUtils.hasText(username)) {
            throw new RuntimeException("未登录或登录已过期");
        }
        if (!StringUtils.hasText(tenantId)) {
            throw new IllegalArgumentException("租户ID不能为空");
        }

        User user = getCurrentUserInfo(username);
        Tenant tenant = getEnabledTenantById(tenantId);
        if (tenant == null) {
            throw new IllegalArgumentException("租户不存在或已冻结");
        }
        if (!userBelongsToTenant(user.getId(), tenantId) && !isPlatformAdmin(user.getId())) {
            throw new IllegalArgumentException("当前用户未分配该租户");
        }

        // 超级管理员切换到业务租户时，会叠加目标租户默认管理员角色权限，保证跨租户运维可用。
        List<String> permissionCodes = getUserPermissionCodesInTenant(user.getId(), tenantId);
        List<String> roleCodes = getUserRoleCodesInTenant(user.getId(), tenantId);

        if (StringUtils.hasText(currentToken)) {
            tokenService.removeToken(currentToken);
        }

        String token = tokenService.generateAndStoreTokenWithTenant(
                user.getUsername(),
                user.getId(),
                user.getRealname(),
                permissionCodes,
                roleCodes,
                tenantId
        );

        user.setLoginTenantId(tenantId);
        userMapper.updateById(user);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("tenantId", tenantId);
        result.put("tenantName", tenant.getName());
        log.info("用户 {} 切换租户成功，租户ID: {}", username, tenantId);
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

        if (!StringUtils.hasText(newPassword) || !newPassword.matches(PASSWORD_PATTERN)) {
            throw new IllegalArgumentException("密码需为8-20位，包含大小写字母、数字和特殊字符");
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
        
        String currentTenantId = TenantContextHolder.getCurrentTenantId();

        // 2. 获取用户权限列表
        List<String> permissionCodes = getUserPermissionCodesInTenant(user.getId(), currentTenantId);
        log.info("用户 {} 的权限列表: {}", username, permissionCodes);
        
        // 3. 获取用户角色列表
        List<String> roleCodes = getUserRoleCodesInTenant(user.getId(), currentTenantId);
        log.info("用户 {} 的角色列表: {}", username, roleCodes);
        
        // 4. 构建返回结果
        UserInfoVO userInfoVO = new UserInfoVO();
        userInfoVO.setUserId(user.getId());
        userInfoVO.setUsername(user.getUsername());
        userInfoVO.setRealName(user.getRealname());
        userInfoVO.setAvatar(buildAvatarDisplayUrl(user.getAvatar()));
        userInfoVO.setPermissions(permissionCodes);
        userInfoVO.setRoles(roleCodes);
        userInfoVO.setTenantId(currentTenantId);
        Tenant currentTenant = getEnabledTenantById(currentTenantId);
        if (currentTenant != null) {
            userInfoVO.setTenantName(currentTenant.getName());
        }
        userInfoVO.setHomePath(resolveHomePath(user.getId(), currentTenantId));
        
        log.info("获取用户详细信息成功：{}, 权限数量: {}, 角色数量: {}", username, permissionCodes.size(), roleCodes.size());
        return userInfoVO;
    }

    @Override
    public CurrentUserProfileVO getCurrentUserProfile(String username) {
        User user = getEnabledUserByUsername(username);
        return buildCurrentUserProfile(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CurrentUserProfileVO updateCurrentUserProfile(String username, CurrentUserProfileReq req) {
        User user = getEnabledUserByUsername(username);
        CurrentUserProfileReq normalizedReq = normalizeCurrentUserProfileReq(req);
        validateCurrentUserProfileUnique(user.getId(), normalizedReq);

        User update = new User();
        update.setId(user.getId());
        update.setRealname(normalizedReq.getRealname());
        update.setEmail(normalizedReq.getEmail());
        update.setPhone(normalizedReq.getPhone());
        update.setTelephone(normalizedReq.getTelephone());
        update.setPost(normalizedReq.getPost());
        update.setUpdateBy(username);
        update.setUpdateTime(java.time.LocalDateTime.now());
        baseMapper.updateById(update);

        return getCurrentUserProfile(username);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CurrentUserProfileVO uploadCurrentUserAvatar(String username, MultipartFile file) {
        User user = getEnabledUserByUsername(username);
        validateAvatarFile(file);
        FileUploadReq uploadReq = new FileUploadReq();
        uploadReq.setBizType("user-avatar");
        uploadReq.setBizId(user.getId());
        var uploadVO = sysFilesService.uploadFile(username, file, uploadReq);

        User update = new User();
        update.setId(user.getId());
        update.setAvatar(uploadVO.getFileId());
        update.setUpdateBy(username);
        update.setUpdateTime(java.time.LocalDateTime.now());
        baseMapper.updateById(update);
        return getCurrentUserProfile(username);
    }

    @Override
    public CurrentUserOrganizationVO getCurrentUserOrganization(String username) {
        User user = getEnabledUserByUsername(username);
        CurrentUserOrganizationVO vo = new CurrentUserOrganizationVO();
        vo.setDeparts(BeanUtil.copyToList(getUserDeparts(user.getId()), SysDepartVO.class));
        List<Role> roles = getUserRoles(user.getId());
        vo.setRoles(BeanUtil.copyToList(roles, RoleVO.class));
        List<DepartRole> departRoles = getUserDepartRoles(user.getId());
        vo.setDepartRoles(BeanUtil.copyToList(departRoles, DepartRoleVO.class));
        List<Permission> permissions = getUserPermissionsInTenant(
                user.getId(),
                TenantContextHolder.getCurrentTenantId(),
                roles,
                departRoles);
        List<CurrentUserPermissionSummaryVO> menuPermissions = buildUserMenuPermissionSummaries(permissions);
        vo.setMenuPermissions(menuPermissions);
        vo.setMenuPermissionCount(countCurrentUserPermissionNodes(menuPermissions));
        return vo;
    }

    @Override
    public List<CurrentUserTenantVO> getCurrentUserTenantOptions(String username) {
        List<Tenant> tenants = getCurrentUserTenants(username);
        String currentTenantId = TenantContextHolder.getCurrentTenantId();
        return tenants.stream()
                .map(tenant -> {
                    CurrentUserTenantVO vo = BeanUtil.copyProperties(tenant, CurrentUserTenantVO.class);
                    vo.setCurrent(StringUtils.hasText(currentTenantId) && currentTenantId.equals(tenant.getId()));
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public PageVO<CurrentUserLogVO> pageCurrentUserLogs(String username, BasePageReq req) {
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("未登录或登录已过期");
        }

        int pageNum = req == null ? 1 : Math.max(req.getPageNum(), 1);
        int pageSize = req == null ? 10 : Math.max(req.getPageSize(), 1);
        validateCurrentUserLogQuery(req);
        QueryWrapper<SysLog> wrapper = req == null
                ? new QueryWrapper<>()
                : QueryWrapperBuilderUtils.build(req);
        wrapper.eq("delete_flag", 0)
                .eq("username", username);

        if (req == null || !StringUtils.hasText(req.getSortField())) {
            wrapper.orderByDesc("create_time");
        }

        Page<SysLog> page = new Page<>(pageNum, pageSize);
        Page<SysLog> resultPage = logMapper.selectPage(page, wrapper);
        List<CurrentUserLogVO> records = BeanUtil.copyToList(resultPage.getRecords(), CurrentUserLogVO.class);
        return new PageVO<>(records, resultPage.getTotal(), resultPage.getCurrent(), resultPage.getSize());
    }

    /**
     * 个人日志接口只能筛选和排序允许展示的字段，避免通过个人中心探测系统日志敏感列。
     */
    private void validateCurrentUserLogQuery(BasePageReq req) {
        if (req == null) {
            return;
        }

        if (StringUtils.hasText(req.getSortField())) {
            for (String sortField : req.getSortField().split(",")) {
                if (!CURRENT_USER_LOG_QUERY_FIELDS.contains(sortField.trim())) {
                    throw new IllegalArgumentException("不支持的日志排序字段");
                }
            }
        }

        if (req.getQueryParams() == null || req.getQueryParams().isEmpty()) {
            return;
        }

        for (String queryKey : req.getQueryParams().keySet()) {
            String fieldName = queryKey;
            int lastUnderscore = queryKey.lastIndexOf('_');
            if (lastUnderscore > 0) {
                fieldName = queryKey.substring(0, lastUnderscore);
            }
            if (!CURRENT_USER_LOG_QUERY_FIELDS.contains(fieldName)) {
                throw new IllegalArgumentException("不支持的日志筛选字段");
            }
        }
    }

    /**
     * 根据用户所属部门补齐部门默认角色。
     */
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

    /**
     * 查询用户通过部门角色继承得到的权限。
     */
    private List<Permission> getUserDepartRolePermissions(String userId) {
        List<String> departRoleIds = getUserDepartRoleIds(userId);
        if (departRoleIds.isEmpty()) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<DepartRolePermission> rolePermissionWrapper = new LambdaQueryWrapper<>();
        rolePermissionWrapper.in(DepartRolePermission::getRoleId, departRoleIds)
                .eq(DepartRolePermission::getDeleteFlag, 0);
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
                .eq(Permission::getDeleteFlag, 0)
                .eq(Permission::getStatus, "1")
                .orderByAsc(Permission::getSortNo);
        return permissionMapper.selectList(permissionWrapper);
    }

    /**
     * 查询用户直接分配角色的角色编码。
     */
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

    /**
     * 查询用户在指定租户下最终生效的权限编码。
     */
    private List<String> getUserPermissionCodesInTenant(String userId, String tenantId) {
        return getUserPermissionsInTenant(userId, tenantId).stream()
                .map(Permission::getPerms)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 首页路径必须来自当前租户实际可访问菜单，避免低权限用户登录后跳到无菜单入口的历史页面。
     */
    private String resolveHomePath(String userId, String tenantId) {
        List<Permission> menuPermissions = getUserPermissionsInTenant(userId, tenantId).stream()
                .filter(permission -> PermissionMenuTypes.isMenu(permission.getMenuType()))
                .filter(permission -> permission.getHidden() == null || permission.getHidden() != 1)
                .filter(permission -> StringUtils.hasText(permission.getUrl()))
                .toList();
        if (menuPermissions.isEmpty()) {
            return DEFAULT_HOME_PATH;
        }

        Map<String, List<Permission>> childrenByParentId = menuPermissions.stream()
                .filter(permission -> StringUtils.hasText(permission.getParentId()))
                .collect(Collectors.groupingBy(Permission::getParentId));

        return menuPermissions.stream()
                .filter(permission -> !StringUtils.hasText(permission.getParentId()))
                .sorted(this::comparePermissionSort)
                .map(permission -> resolveFirstOpenableMenuPath(permission, childrenByParentId))
                .filter(StringUtils::hasText)
                .findFirst()
                .orElseGet(() -> menuPermissions.stream()
                        .sorted(this::comparePermissionSort)
                        .map(Permission::getUrl)
                        .filter(StringUtils::hasText)
                        .findFirst()
                        .orElse(DEFAULT_HOME_PATH));
    }

    private String resolveFirstOpenableMenuPath(Permission permission, Map<String, List<Permission>> childrenByParentId) {
        List<Permission> children = childrenByParentId.getOrDefault(permission.getId(), List.of()).stream()
                .sorted(this::comparePermissionSort)
                .toList();
        if (children.isEmpty()) {
            return permission.getUrl();
        }
        return children.stream()
                .map(child -> resolveFirstOpenableMenuPath(child, childrenByParentId))
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(permission.getUrl());
    }

    private int comparePermissionSort(Permission left, Permission right) {
        if (left.getSortNo() == null && right.getSortNo() == null) {
            return Objects.toString(left.getCreateTime(), "").compareTo(Objects.toString(right.getCreateTime(), ""));
        }
        if (left.getSortNo() == null) {
            return 1;
        }
        if (right.getSortNo() == null) {
            return -1;
        }
        int sortCompare = left.getSortNo().compareTo(right.getSortNo());
        if (sortCompare != 0) {
            return sortCompare;
        }
        return Objects.toString(left.getCreateTime(), "").compareTo(Objects.toString(right.getCreateTime(), ""));
    }

    /**
     * 查询当前租户下已授权的菜单和权限点树，仅返回中文名称，不暴露权限编码。
     */
    private List<CurrentUserPermissionSummaryVO> getUserMenuPermissionSummaries(String userId, String tenantId) {
        return buildUserMenuPermissionSummaries(getUserPermissionsInTenant(userId, tenantId));
    }

    private List<CurrentUserPermissionSummaryVO> buildUserMenuPermissionSummaries(List<Permission> permissions) {
        Map<String, CurrentUserPermissionSummaryVO> nodeMap = permissions.stream()
                .filter(permission -> StringUtils.hasText(permission.getId()))
                .filter(permission -> StringUtils.hasText(permission.getName()))
                .collect(Collectors.toMap(
                        Permission::getId,
                        permission -> {
                            CurrentUserPermissionSummaryVO vo = new CurrentUserPermissionSummaryVO();
                            vo.setId(permission.getId());
                            vo.setParentId(permission.getParentId());
                            vo.setName(permission.getName());
                            vo.setMenuType(permission.getMenuType());
                            return vo;
                        },
                        (left, right) -> left,
                        java.util.LinkedHashMap::new
                ));
        List<CurrentUserPermissionSummaryVO> roots = new ArrayList<>();

        nodeMap.values().forEach(node -> {
            CurrentUserPermissionSummaryVO parent = nodeMap.get(node.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        });
        return roots;
    }

    /**
     * 递归统计当前用户已授权的菜单和权限点数量，树形返回时不能只统计根节点。
     */
    private int countCurrentUserPermissionNodes(List<CurrentUserPermissionSummaryVO> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return 0;
        }

        return permissions.stream()
                .mapToInt(permission -> 1 + countCurrentUserPermissionNodes(permission.getChildren()))
                .sum();
    }

    /**
     * 查询用户在指定租户下最终生效的权限对象。
     */
    private List<Permission> getUserPermissionsInTenant(String userId, String tenantId) {
        List<Permission> permissions = new ArrayList<>();
        permissions.addAll(runWithTenant(tenantId, () -> getUserPermissions(userId)));
        if (isTenantAdminPermissionsApplied(userId, tenantId)) {
            permissions.addAll(getRolePermissionsInTenant(tenantId, buildTenantAdminRoleCode(tenantId)));
        }
        if (StringUtils.hasText(tenantId) && !SYSTEM_TENANT_ID.equals(tenantId)) {
            permissions.addAll(runWithTenant(SYSTEM_TENANT_ID, () -> getUserPermissions(userId)));
        }
        return distinctAndSortPermissions(permissions);
    }

    private List<Permission> getUserPermissionsInTenant(
            String userId,
            String tenantId,
            List<Role> tenantRoles,
            List<DepartRole> tenantDepartRoles) {
        List<String> roleIds = tenantRoles.stream()
                .map(Role::getId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        List<String> departRoleIds = tenantDepartRoles.stream()
                .map(DepartRole::getId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        List<Permission> permissions = new ArrayList<>(getPermissionsByRoleIds(roleIds, departRoleIds));
        if (isTenantAdminPermissionsApplied(userId, tenantId)) {
            permissions.addAll(getRolePermissionsInTenant(tenantId, buildTenantAdminRoleCode(tenantId)));
        }
        if (StringUtils.hasText(tenantId) && !SYSTEM_TENANT_ID.equals(tenantId)) {
            permissions.addAll(runWithTenant(SYSTEM_TENANT_ID, () -> getUserPermissions(userId)));
        }
        return distinctAndSortPermissions(permissions);
    }

    /**
     * 查询用户在指定租户下最终生效的角色编码。
     */
    private List<String> getUserRoleCodesInTenant(String userId, String tenantId) {
        Set<String> roleCodes = new LinkedHashSet<>();
        roleCodes.addAll(runWithTenant(tenantId, () -> getUserRoleCodes(userId)));
        if (isTenantAdminPermissionsApplied(userId, tenantId)) {
            roleCodes.add(buildTenantAdminRoleCode(tenantId));
        }
        if (StringUtils.hasText(tenantId) && !"0".equals(tenantId)) {
            roleCodes.addAll(runWithTenant("0", () -> getUserRoleCodes(userId)));
        }
        return new ArrayList<>(roleCodes);
    }

    /**
     * 查询当前用户通过有效部门角色获得的角色列表。
     */
    private List<DepartRole> getUserDepartRoles(String userId) {
        List<String> departRoleIds = getUserDepartRoleIds(userId);
        if (departRoleIds.isEmpty()) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<DepartRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(DepartRole::getId, departRoleIds)
                .eq(DepartRole::getDeleteFlag, 0);
        return departRoleMapper.selectList(wrapper);
    }

    /**
     * 判断超级管理员切换到业务租户时是否需要叠加目标租户管理员角色权限。
     */
    private boolean isTenantAdminPermissionsApplied(String userId, String tenantId) {
        return StringUtils.hasText(tenantId)
                && !SYSTEM_TENANT_ID.equals(tenantId)
                && isPlatformAdmin(userId);
    }

    /**
     * 在指定租户上下文中查询指定角色编码对应的权限。
     */
    private List<Permission> getRolePermissionsInTenant(String tenantId, String roleCode) {
        if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(roleCode)) {
            return new ArrayList<>();
        }

        return runWithTenant(tenantId, () -> {
            LambdaQueryWrapper<Role> roleWrapper = new LambdaQueryWrapper<>();
            roleWrapper.eq(Role::getRoleCode, roleCode)
                    .eq(Role::getDeleteFlag, 0)
                    .last("LIMIT 1");
            Role role = roleMapper.selectOne(roleWrapper);
            if (role == null || !StringUtils.hasText(role.getId())) {
                return new ArrayList<>();
            }

            LambdaQueryWrapper<RolePermission> rolePermissionWrapper = new LambdaQueryWrapper<>();
            rolePermissionWrapper.eq(RolePermission::getRoleId, role.getId())
                    .eq(RolePermission::getDeleteFlag, 0);
            List<String> permissionIds = rolePermissionMapper.selectList(rolePermissionWrapper).stream()
                    .map(RolePermission::getPermissionId)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .collect(Collectors.toList());
            if (permissionIds.isEmpty()) {
                return new ArrayList<>();
            }

            LambdaQueryWrapper<Permission> permissionWrapper = new LambdaQueryWrapper<>();
            permissionWrapper.in(Permission::getId, permissionIds)
                    .eq(Permission::getDeleteFlag, 0)
                    .eq(Permission::getStatus, "1")
                    .orderByAsc(Permission::getSortNo);
            return permissionMapper.selectList(permissionWrapper);
        });
    }

    /**
     * 按约定生成租户默认管理员角色编码。
     */
    private String buildTenantAdminRoleCode(String tenantId) {
        return TENANT_ADMIN_ROLE_CODE_PREFIX + tenantId.trim();
    }

    /**
     * 临时切换租户上下文执行查询或写入，并在结束后恢复原上下文。
     */
    private <T> T runWithTenant(String tenantId, java.util.function.Supplier<T> supplier) {
        String previousTenantId = TenantContextHolder.getCurrentTenantId();
        if (StringUtils.hasText(tenantId)) {
            TenantContextHolder.setCurrentTenantId(tenantId);
        }
        try {
            return supplier.get();
        } finally {
            if (StringUtils.hasText(previousTenantId)) {
                TenantContextHolder.setCurrentTenantId(previousTenantId);
            } else {
                TenantContextHolder.clear();
            }
        }
    }

    /**
     * 查询用户所属部门对应的部门角色 ID。
     */
    private List<String> getUserDepartRoleIds(String userId) {
        if (!StringUtils.hasText(userId)) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<DepartRoleUser> roleUserWrapper = new LambdaQueryWrapper<>();
        roleUserWrapper.eq(DepartRoleUser::getUserId, userId)
                .eq(DepartRoleUser::getDeleteFlag, 0);
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

    /**
     * 查询部门默认角色 ID。
     */
    private List<String> getDefaultDepartRoleIds(List<String> departIds) {
        List<String> normalizedDepartIds = normalizeIds(departIds);
        if (normalizedDepartIds.isEmpty()) {
            return new ArrayList<>();
        }

        Set<String> departIdSet = new LinkedHashSet<>(normalizedDepartIds);
        Map<String, String> defaultRoleCodeByDepartId = getDefaultRoleCodeByDepartId(normalizedDepartIds);
        LambdaQueryWrapper<DepartRole> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(DepartRole::getDepartId, normalizedDepartIds)
                .eq(DepartRole::getDeleteFlag, 0);
        return departRoleMapper.selectList(wrapper).stream()
                .filter(role -> StringUtils.hasText(role.getDepartId()))
                .filter(role -> departIdSet.contains(role.getDepartId()))
                .filter(role -> isDefaultDepartRole(role, defaultRoleCodeByDepartId.get(role.getDepartId())))
                .map(DepartRole::getId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    private Map<String, String> getDefaultRoleCodeByDepartId(List<String> departIds) {
        LambdaQueryWrapper<SysDepart> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysDepart::getId, departIds)
                .eq(SysDepart::getDeleteFlag, 0);
        return sysDepartMapper.selectList(wrapper).stream()
                .filter(depart -> StringUtils.hasText(depart.getId()))
                .filter(depart -> StringUtils.hasText(depart.getOrgCode()))
                .collect(Collectors.toMap(
                        SysDepart::getId,
                        depart -> DepartRoleCodes.buildDefaultRoleCode(depart.getTenantId(), depart.getOrgCode()),
                        (left, right) -> left
                ));
    }

    private boolean isDefaultDepartRole(DepartRole role, String expectedRoleCode) {
        return StringUtils.hasText(expectedRoleCode) && expectedRoleCode.equals(role.getRoleCode())
                || role.getDepartId().equals(role.getRoleCode());
    }

    /**
     * 查询部门显式绑定的部门角色 ID。
     */
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

    /**
     * 合并直接角色权限和部门角色权限。
     */
    private List<Permission> mergePermissions(List<Permission> rolePermissions, List<Permission> departRolePermissions) {
        List<Permission> permissions = new ArrayList<>();
        permissions.addAll(rolePermissions);
        permissions.addAll(departRolePermissions);
        return distinctAndSortPermissions(permissions);
    }

    private List<Permission> getPermissionsByRoleIds(List<String> roleIds, List<String> departRoleIds) {
        Set<String> permissionIds = new LinkedHashSet<>();
        List<String> normalizedRoleIds = normalizeIds(roleIds);
        if (!normalizedRoleIds.isEmpty()) {
            rolePermissionMapper.selectList(new LambdaQueryWrapper<RolePermission>()
                            .in(RolePermission::getRoleId, normalizedRoleIds)
                            .eq(RolePermission::getDeleteFlag, 0))
                    .stream()
                    .map(RolePermission::getPermissionId)
                    .filter(StringUtils::hasText)
                    .forEach(permissionIds::add);
        }

        List<String> normalizedDepartRoleIds = normalizeIds(departRoleIds);
        if (!normalizedDepartRoleIds.isEmpty()) {
            departRolePermissionMapper.selectList(new LambdaQueryWrapper<DepartRolePermission>()
                            .in(DepartRolePermission::getRoleId, normalizedDepartRoleIds)
                            .eq(DepartRolePermission::getDeleteFlag, 0))
                    .stream()
                    .map(DepartRolePermission::getPermissionId)
                    .filter(StringUtils::hasText)
                    .forEach(permissionIds::add);
        }

        if (permissionIds.isEmpty()) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<Permission> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Permission::getId, permissionIds)
                .eq(Permission::getDeleteFlag, 0)
                .eq(Permission::getStatus, "1")
                .orderByAsc(Permission::getSortNo);
        return distinctAndSortPermissions(permissionMapper.selectList(wrapper));
    }

    /**
     * 对权限列表按 ID 去重，并按 sort 字段排序。
     */
    private List<Permission> distinctAndSortPermissions(List<Permission> sourcePermissions) {
        Set<String> permissionIds = new LinkedHashSet<>();
        List<Permission> permissions = new ArrayList<>();
        for (Permission permission : sourcePermissions) {
            if (permission != null && StringUtils.hasText(permission.getId()) && permissionIds.add(permission.getId())) {
                permissions.add(permission);
            }
        }
        permissions.sort((p1, p2) -> {
            if (p1.getSortNo() == null) return 1;
            if (p2.getSortNo() == null) return -1;
            return p1.getSortNo().compareTo(p2.getSortNo());
        });
        return permissions;
    }

    /**
     * 获取用户的默认租户ID
     * 从UserTenant关系中获取第一个正常状态的租户
     */
    private boolean isPlatformOperator(BaseDTO<User> dto) {
        if (dto == null || dto.getContext() == null) {
            return false;
        }
        return isPlatformOperatorUsername(dto.getContext().getUsername());
    }

    /**
     * 判断指定账号是否属于系统默认 ADMIN 超级管理员角色。
     */
    private boolean isPlatformOperatorUsername(String username) {
        if (!StringUtils.hasText(username) || "anonymous".equals(username)) {
            return false;
        }
        User operator = getCurrentUserInfo(username);
        return isPlatformAdmin(operator.getId());
    }

    /**
     * 判断指定用户是否属于系统默认 ADMIN 超级管理员角色。
     */
    private boolean isPlatformAdmin(String userId) {
        if (!StringUtils.hasText(userId)) {
            return false;
        }
        return runWithTenant(SYSTEM_TENANT_ID, () -> getUserRoleCodes(userId).stream()
                .anyMatch(PLATFORM_ADMIN_ROLE_CODE::equals));
    }

    /**
     * 查询所有启用且未删除的租户。
     */
    private List<Tenant> getEnabledTenants() {
        LambdaQueryWrapper<Tenant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Tenant::getDeleteFlag, 0)
                .eq(Tenant::getStatus, 1)
                .orderByAsc(Tenant::getId);
        return tenantMapper.selectList(wrapper);
    }

    /**
     * 从当前租户上下文中获取必填租户 ID。
     */
    private String getRequiredTenantId() {
        String tenantId = TenantContextHolder.getCurrentTenantId();
        if (!StringUtils.hasText(tenantId)) {
            throw new IllegalArgumentException("当前租户不能为空");
        }
        return tenantId;
    }

    /**
     * 优先从请求上下文获取租户 ID，缺省时回退到线程租户上下文。
     */
    private String getRequiredTenantId(BaseDTO<User> dto) {
        String tenantId = dto != null && dto.getContext() != null ? dto.getContext().getTenantId() : null;
        if (!StringUtils.hasText(tenantId)) {
            tenantId = TenantContextHolder.getCurrentTenantId();
        }
        if (!StringUtils.hasText(tenantId)) {
            throw new IllegalArgumentException("当前租户不能为空");
        }
        return tenantId;
    }

    /**
     * 查询指定租户的正常成员用户 ID。
     */
    private List<String> getTenantMemberUserIds(String tenantId) {
        LambdaQueryWrapper<UserTenant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserTenant::getTenantId, tenantId)
                .eq(UserTenant::getStatus, "1")
                .eq(UserTenant::getDeleteFlag, 0);
        return userTenantMapper.selectList(wrapper).stream()
                .map(UserTenant::getUserId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 获取用户默认登录租户，优先使用最近登录租户。
     */
    private String getDefaultTenantId(String userId) {
        User user = userMapper.selectById(userId);
        if (user != null && StringUtils.hasText(user.getLoginTenantId())
                && (userBelongsToTenant(userId, user.getLoginTenantId()) || isPlatformAdmin(userId))
                && getEnabledTenantById(user.getLoginTenantId()) != null) {
            return user.getLoginTenantId();
        }

        LambdaQueryWrapper<UserTenant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserTenant::getUserId, userId)
               .eq(UserTenant::getStatus, "1")  // 状态为正常
               .eq(UserTenant::getDeleteFlag, 0)
               .orderByAsc(UserTenant::getCreateTime)
               .last("LIMIT 1");
        
        UserTenant userTenant = userTenantMapper.selectOne(wrapper);
        
        if (userTenant != null) {
            return userTenant.getTenantId();
        }
        
        // 如果没有关联的租户，返回默认租户ID "0"
        return "0";
    }

    /**
     * 清洗 ID 列表，去空、去重并保持传入顺序。
     */
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

    /**
     * 校验角色 ID 列表对应的角色存在且未删除。
     */
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

    /**
     * 校验租户内用户分配角色的边界，包括成员归属、角色租户和权限范围。
     */
    private void validateTenantRoleAssignment(
            String userId,
            List<String> roleIds,
            String operatorUsername,
            boolean platformOperator) {
        if (platformOperator) {
            return;
        }

        if (!StringUtils.hasText(operatorUsername)) {
            throw new IllegalArgumentException("无法识别当前操作人");
        }

        String tenantId = getRequiredTenantId();
        if (!userBelongsToTenant(userId, tenantId)) {
            throw new IllegalArgumentException("只能为当前租户成员分配角色");
        }

        List<Role> roles = getRolesByIds(roleIds);
        List<String> invalidRoleCodes = roles.stream()
                .filter(role -> !tenantId.equals(role.getTenantId()) || PLATFORM_ADMIN_ROLE_CODE.equals(role.getRoleCode()))
                .map(Role::getRoleCode)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (!invalidRoleCodes.isEmpty()) {
            throw new IllegalArgumentException("只能分配当前租户角色，且不能分配平台超级管理员角色: " + String.join(",", invalidRoleCodes));
        }

        validateRoleGrantWithinOperatorPermissions(roleIds, operatorUsername);
    }

    /**
     * 批量查询未删除角色。
     */
    private List<Role> getRolesByIds(List<String> roleIds) {
        if (roleIds.isEmpty()) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Role::getId, roleIds)
                .eq(Role::getDeleteFlag, 0);
        return roleMapper.selectList(wrapper);
    }

    /**
     * 校验被分配角色的权限集合不超过当前操作人自身权限范围。
     */
    private void validateRoleGrantWithinOperatorPermissions(List<String> roleIds, String operatorUsername) {
        if (roleIds.isEmpty()) {
            return;
        }

        Set<String> operatorPerms = getUserPermissionCodesByUsername(operatorUsername).stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());

        Set<String> rolePerms = getPermissionCodesByRoleIds(roleIds);
        List<String> overLimitPerms = rolePerms.stream()
                .filter(perms -> !operatorPerms.contains(perms))
                .distinct()
                .collect(Collectors.toList());
        if (!overLimitPerms.isEmpty()) {
            throw new IllegalArgumentException("不能分配超出自身权限范围的角色: " + String.join(",", overLimitPerms));
        }
    }

    /**
     * 查询一批角色拥有的权限编码集合。
     */
    private Set<String> getPermissionCodesByRoleIds(List<String> roleIds) {
        if (roleIds.isEmpty()) {
            return new LinkedHashSet<>();
        }

        LambdaQueryWrapper<RolePermission> rolePermissionWrapper = new LambdaQueryWrapper<>();
        rolePermissionWrapper.in(RolePermission::getRoleId, roleIds)
                .eq(RolePermission::getDeleteFlag, 0);
        List<String> permissionIds = rolePermissionMapper.selectList(rolePermissionWrapper).stream()
                .map(RolePermission::getPermissionId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (permissionIds.isEmpty()) {
            return new LinkedHashSet<>();
        }

        LambdaQueryWrapper<Permission> permissionWrapper = new LambdaQueryWrapper<>();
        permissionWrapper.in(Permission::getId, permissionIds)
                .eq(Permission::getDeleteFlag, 0)
                .eq(Permission::getStatus, "1");
        return permissionMapper.selectList(permissionWrapper).stream()
                .map(Permission::getPerms)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 校验租户 ID 列表对应的租户存在且未删除。
     */
    private void validateTenants(List<String> tenantIds) {
        if (tenantIds.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<Tenant> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Tenant::getId, tenantIds)
               .eq(Tenant::getDeleteFlag, 0)
               .eq(Tenant::getStatus, 1);
        long count = tenantMapper.selectCount(wrapper);
        if (count != tenantIds.size()) {
            throw new IllegalArgumentException("包含不存在或已冻结的租户");
        }
    }

    /**
     * 校验用户 ID 列表对应的用户存在且未删除。
     */
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

    /**
     * 以用户为主维度差量同步用户可访问租户。
     */
    private void syncUserTenants(String userId, List<String> targetTenantIds) {
        LambdaQueryWrapper<UserTenant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserTenant::getUserId, userId);
        List<UserTenant> existingRelations = userTenantMapper.selectList(wrapper);

        Set<String> targetTenantIdSet = new LinkedHashSet<>(targetTenantIds);
        Set<String> existingNormalTenantIdSet = existingRelations.stream()
                .filter(relation -> relation.getDeleteFlag() == null || relation.getDeleteFlag() == 0)
                .filter(relation -> "1".equals(relation.getStatus()))
                .map(UserTenant::getTenantId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> deleteIds = existingRelations.stream()
                .filter(relation -> StringUtils.hasText(relation.getTenantId()))
                .filter(relation -> !targetTenantIdSet.contains(relation.getTenantId()))
                .map(UserTenant::getId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        // 覆盖保存时只逻辑删除被取消选择的租户关系，保留未变化的有效关系。
        deleteUserTenantRelations(deleteIds);

        for (String tenantId : targetTenantIdSet) {
            if (existingNormalTenantIdSet.contains(tenantId)) {
                continue;
            }
            upsertUserTenantRelation(userId, tenantId);
        }
    }

    /**
     * 以租户为主维度差量同步租户成员。
     */
    private void syncTenantUsers(String tenantId, List<String> targetUserIds) {
        LambdaQueryWrapper<UserTenant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserTenant::getTenantId, tenantId);
        List<UserTenant> existingRelations = userTenantMapper.selectList(wrapper);

        Set<String> targetUserIdSet = new LinkedHashSet<>(targetUserIds);
        Set<String> existingNormalUserIdSet = existingRelations.stream()
                .filter(relation -> relation.getDeleteFlag() == null || relation.getDeleteFlag() == 0)
                .filter(relation -> "1".equals(relation.getStatus()))
                .map(UserTenant::getUserId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<String> deleteIds = existingRelations.stream()
                .filter(relation -> StringUtils.hasText(relation.getUserId()))
                .filter(relation -> !targetUserIdSet.contains(relation.getUserId()))
                .map(UserTenant::getId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        // 租户成员调整采用差量同步：取消选择的成员关系逻辑删除，已存在成员不重复插入。
        deleteUserTenantRelations(deleteIds);

        for (String userId : targetUserIdSet) {
            if (existingNormalUserIdSet.contains(userId)) {
                continue;
            }
            upsertUserTenantRelation(userId, tenantId);
        }
    }

    /**
     * 按关系 ID 逻辑删除用户-租户关系。
     */
    private void deleteUserTenantRelations(List<String> ids) {
        if (ids.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<UserTenant> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(UserTenant::getId, ids)
                .eq(UserTenant::getDeleteFlag, 0);
        softDeleteUserTenants(wrapper, "system");
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
     * 按条件逻辑删除用户-角色关系。
     */
    private void softDeleteUserRoles(LambdaQueryWrapper<UserRole> wrapper, String deleteBy) {
        UserRole userRole = new UserRole();
        EntityFillUtils.fillDeleteFields(userRole, deleteBy);
        userRoleMapper.update(userRole, wrapper);
    }

    /**
     * 按条件逻辑删除用户-部门关系。
     */
    private void softDeleteUserDeparts(LambdaQueryWrapper<UserDepart> wrapper, String deleteBy) {
        UserDepart userDepart = new UserDepart();
        EntityFillUtils.fillDeleteFields(userDepart, deleteBy);
        userDepartMapper.update(userDepart, wrapper);
    }

    /**
     * 按条件逻辑删除部门角色-用户关系。
     */
    private void softDeleteDepartRoleUsers(LambdaQueryWrapper<DepartRoleUser> wrapper, String deleteBy) {
        DepartRoleUser departRoleUser = new DepartRoleUser();
        EntityFillUtils.fillDeleteFields(departRoleUser, deleteBy);
        departRoleUserMapper.update(departRoleUser, wrapper);
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
            // 关系存在但已逻辑删除时恢复，避免唯一关系被重复插入。
            if (existingRelation.getDeleteFlag() != null && existingRelation.getDeleteFlag() == 1) {
                existingRelation.setDeleteFlag(0);
                existingRelation.setDeleteTime(null);
                existingRelation.setDeleteBy(null);
            }
            existingRelation.setStatus("1");
            userTenantMapper.updateById(existingRelation);
            return;
        }

        UserTenant userTenant = new UserTenant();
        userTenant.setId(newId());
        userTenant.setUserId(userId);
        userTenant.setTenantId(tenantId);
        userTenant.setStatus("1");
        userTenantMapper.insert(userTenant);
    }

    /**
     * 判断用户是否为指定租户的正常成员。
     */
    private boolean userBelongsToTenant(String userId, String tenantId) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(tenantId)) {
            return false;
        }

        LambdaQueryWrapper<UserTenant> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserTenant::getUserId, userId)
               .eq(UserTenant::getTenantId, tenantId)
               .eq(UserTenant::getStatus, "1")
               .eq(UserTenant::getDeleteFlag, 0);
        return userTenantMapper.selectCount(wrapper) > 0;
    }

    /**
     * 查询指定 ID 对应的启用租户。
     */
    private Tenant getEnabledTenantById(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return null;
        }

        Tenant tenant = tenantMapper.selectById(tenantId);
        if (tenant == null || (tenant.getDeleteFlag() != null && tenant.getDeleteFlag() == 1)) {
            return null;
        }
        if (tenant.getStatus() == null || tenant.getStatus() != 1) {
            return null;
        }
        return tenant;
    }

    /**
     * 按用户名查询当前登录的启用用户。
     */
    private User getEnabledUserByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("未登录或登录已过期");
        }

        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username)
                .eq(User::getDeleteFlag, 0)
                .last("LIMIT 1");
        User user = userMapper.selectOne(wrapper);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new IllegalArgumentException("用户不存在或已被冻结");
        }
        return user;
    }

    /**
     * 构建个人中心基础资料，并补齐当前租户名称。
     */
    private CurrentUserProfileVO buildCurrentUserProfile(User user) {
        CurrentUserProfileVO vo = BeanUtil.copyProperties(user, CurrentUserProfileVO.class);
        vo.setAvatar(buildAvatarDisplayUrl(user.getAvatar()));
        String currentTenantId = TenantContextHolder.getCurrentTenantId();
        vo.setTenantId(currentTenantId);
        Tenant tenant = getEnabledTenantById(currentTenantId);
        if (tenant != null) {
            vo.setTenantName(tenant.getName());
        }
        return vo;
    }

    /**
     * 清洗个人资料可编辑字段，避免空白字符串绕过唯一性校验。
     */
    private CurrentUserProfileReq normalizeCurrentUserProfileReq(CurrentUserProfileReq req) {
        CurrentUserProfileReq normalized = req == null ? new CurrentUserProfileReq() : req;
        normalized.setRealname(trimToNull(normalized.getRealname()));
        normalized.setEmail(trimToNull(normalized.getEmail()));
        normalized.setPhone(trimToNull(normalized.getPhone()));
        normalized.setTelephone(trimToNull(normalized.getTelephone()));
        normalized.setPost(trimToNull(normalized.getPost()));
        return normalized;
    }

    /**
     * 头像字段持久化 MinIO 对象名；返回前端时转换为可浏览器访问的预签名 URL。
     */
    private String buildAvatarDisplayUrl(String avatar) {
        if (!StringUtils.hasText(avatar)) {
            return avatar;
        }
        try {
            return sysFilesService.getFileById(avatar).getFileUrl();
        } catch (RuntimeException e) {
            log.warn("生成头像访问地址失败: {}", e.getMessage());
            return avatar;
        }
    }

    /**
     * 头像上传只允许常见图片格式，并限制大小，避免把个人中心变成任意文件上传入口。
     */
    private void validateAvatarFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("头像文件不能为空");
        }
        if (file.getSize() > 2 * 1024 * 1024L) {
            throw new IllegalArgumentException("头像文件不能超过2MB");
        }
        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType) || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("仅支持上传图片文件");
        }
    }

    /**
     * 查询指定 ID 对应的启用用户。
     */
    private User getEnabledUserById(String userId) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }

        User user = userMapper.selectById(userId);
        if (user == null || (user.getDeleteFlag() != null && user.getDeleteFlag() == 1)) {
            return null;
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            return null;
        }
        return user;
    }

    /**
     * 从请求上下文解析操作人账号。
     */
    private String resolveOperator(BaseDTO<User> dto) {
        if (dto != null && dto.getContext() != null && StringUtils.hasText(dto.getContext().getUsername())) {
            return dto.getContext().getUsername();
        }
        return "system";
    }

    /**
     * 在已捕获异常并返回失败响应前，显式标记当前事务回滚。
     */
    private void markRollbackOnly() {
        try {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        } catch (Exception ignored) {
            // No active transaction when invoked outside Spring proxy.
        }
    }

    /**
     * 生成项目统一使用的字符串主键。
     */
    private String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 从删除请求中解析单个或批量删除 ID。
     */
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

    /**
     * 用户权限或租户关系发生变化后，清理该用户所有登录态。
     */
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
