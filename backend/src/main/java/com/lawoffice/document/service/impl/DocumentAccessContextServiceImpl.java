package com.lawoffice.document.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lawoffice.document.dto.DocumentAccessContext;
import com.lawoffice.document.dto.DocumentRequestCache;
import com.lawoffice.system.entity.SysDepart;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.entity.UserDepart;
import com.lawoffice.system.entity.UserRole;
import com.lawoffice.system.mapper.SysDepartMapper;
import com.lawoffice.system.mapper.UserDepartMapper;
import com.lawoffice.system.mapper.UserMapper;
import com.lawoffice.system.mapper.UserRoleMapper;
import com.lawoffice.document.service.IDocumentAccessContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentAccessContextServiceImpl implements IDocumentAccessContextService {

    private final UserMapper userMapper;
    private final UserDepartMapper userDepartMapper;
    private final UserRoleMapper userRoleMapper;
    private final SysDepartMapper sysDepartMapper;

    @Override
    public DocumentAccessContext buildDocumentAccessContext(String username, String tenantId) {
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("当前用户不能为空");
        }
        User user = userMapper.selectOne(Wrappers.lambdaQuery(User.class)
                .eq(User::getUsername, username)
                .eq(User::getDeleteFlag, 0)
                .last("LIMIT 1"));
        if (user == null) {
            throw new IllegalArgumentException("当前用户不存在");
        }
        List<String> departIds = userDepartMapper.selectList(Wrappers.lambdaQuery(UserDepart.class)
                        .select(UserDepart::getDepId)
                        .eq(UserDepart::getTenantId, tenantId)
                        .eq(UserDepart::getUserId, user.getId())
                        .eq(UserDepart::getDeleteFlag, 0))
                .stream()
                .map(UserDepart::getDepId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        departIds = resolveDepartIdsWithAncestors(departIds, tenantId);
        List<String> roleIds = userRoleMapper.selectList(Wrappers.lambdaQuery(UserRole.class)
                        .select(UserRole::getRoleId)
                        .eq(UserRole::getTenantId, tenantId)
                        .eq(UserRole::getUserId, user.getId())
                        .eq(UserRole::getDeleteFlag, 0))
                .stream()
                .map(UserRole::getRoleId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        return new DocumentAccessContext(username, user.getId(), tenantId, departIds, roleIds, new DocumentRequestCache());
    }

    /**
     * 部门共享要继承父级部门授权，构建上下文时把当前部门和祖先部门一起纳入权限匹配范围。
     */
    private List<String> resolveDepartIdsWithAncestors(List<String> departIds, String tenantId) {
        if (departIds == null || departIds.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, String> parentIdByDepartId = new HashMap<>();
        for (SysDepart depart : sysDepartMapper.selectList(Wrappers.lambdaQuery(SysDepart.class)
                        .select(SysDepart::getId, SysDepart::getParentId)
                        .eq(SysDepart::getTenantId, tenantId)
                        .eq(SysDepart::getDeleteFlag, 0))) {
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
}
