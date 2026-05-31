package com.lawoffice.system.service.impl;

import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.framework.dto.BasePageDTO;
import com.lawoffice.framework.dto.TreeDTO;
import com.lawoffice.framework.service.impl.TreeServiceImpl;
import com.lawoffice.system.constant.PermissionMenuTypes;
import com.lawoffice.system.entity.DepartPermission;
import com.lawoffice.system.entity.Permission;
import com.lawoffice.system.entity.RolePermission;
import com.lawoffice.system.mapper.DepartPermissionMapper;
import com.lawoffice.system.mapper.PermissionMapper;
import com.lawoffice.system.mapper.RolePermissionMapper;
import com.lawoffice.system.service.IUserService;
import com.lawoffice.system.service.IPermissionService;
import com.lawoffice.system.vo.PermissionVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PermissionServiceImpl extends TreeServiceImpl<PermissionMapper, Permission, PermissionVO> implements IPermissionService {

    private final RolePermissionMapper rolePermissionMapper;

    private final DepartPermissionMapper departPermissionMapper;

    private final IUserService userService;

    public PermissionServiceImpl(
            RolePermissionMapper rolePermissionMapper,
            DepartPermissionMapper departPermissionMapper,
            IUserService userService) {
        this.rolePermissionMapper = rolePermissionMapper;
        this.departPermissionMapper = departPermissionMapper;
        this.userService = userService;
    }

    @Override
    public List<PermissionVO> tree() {
        TreeDTO<Permission> treeDTO = new TreeDTO<>();
        return tree(treeDTO).getData();
    }

    @Override
    protected void doBeforeList(BaseDTO<Permission> baseDTO) {
        applyPermissionOrder(resolveQueryWrapper(baseDTO));
    }

    @Override
    protected void doBeforePage(BasePageDTO<Permission> basePageDTO) {
        applyPermissionOrder(resolveQueryWrapper(basePageDTO));
    }

    @Override
    public List<PermissionVO> grantableTree(String username) {
        if (!StringUtils.hasText(username)) {
            return new ArrayList<>();
        }

        Set<String> grantablePerms = userService.getUserPermissionCodesByUsername(username).stream()
                .filter(StringUtils::hasText)
                .filter(this::isGrantablePermissionCode)
                .collect(Collectors.toSet());
        if (grantablePerms.isEmpty()) {
            return new ArrayList<>();
        }

        return filterGrantableTree(tree(), grantablePerms);
    }

    @Override
    protected void doBeforeSave(BaseDTO<Permission> saveDTO) {
        Permission permission = saveDTO.getEntity();
        Assert.notNull(permission, "菜单权限数据不能为空");

        normalizePermission(permission);
        validatePermission(permission);
    }

    @Override
    protected void doAfterSave(BaseDTO<Permission> saveDTO, PermissionVO vo) {
        Permission permission = saveDTO.getEntity();
        if (permission == null || vo == null || !StringUtils.hasText(vo.getId())) {
            return;
        }

        clearUnusedFields(vo.getId(), permission.getMenuType());
    }

    @Override
    protected void applyTreeOrder(QueryWrapper<Permission> wrapper) {
        wrapper.orderByAsc("sort_no");
    }

    @Override
    protected Comparator<PermissionVO> treeNodeComparator() {
        return Comparator.comparing(
                PermissionVO::getSortNo,
                Comparator.nullsLast(Integer::compareTo)
        );
    }

    @Override
    protected void doBeforeDelete(BaseDTO<Permission> deleteDTO) {
        try {
            validateNoChildrenBeforeDelete(deleteDTO);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("存在子级菜单或按钮，请先删除子级");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    protected void doAfterDelete(BaseDTO<Permission> deleteDTO) {
        List<String> ids = resolveDeleteIds(deleteDTO);
        if (ids.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<RolePermission> roleWrapper = new LambdaQueryWrapper<>();
        roleWrapper.in(RolePermission::getPermissionId, ids);
        rolePermissionMapper.delete(roleWrapper);

        LambdaQueryWrapper<DepartPermission> departWrapper = new LambdaQueryWrapper<>();
        departWrapper.in(DepartPermission::getPermissionId, ids);
        departPermissionMapper.delete(departWrapper);
    }

    private void normalizePermission(Permission permission) {
        trimTextFields(permission);
        applyDefaultValues(permission);

        switch (permission.getMenuType()) {
            case PermissionMenuTypes.FIRST_LEVEL_MENU -> {
                permission.setParentId(null);
                permission.setPerms(null);
                permission.setKeepAlive(null);
            }
            case PermissionMenuTypes.SUB_MENU -> {
                permission.setPerms(null);
                permission.setRedirect(null);
                permission.setKeepAlive(Objects.requireNonNullElse(permission.getKeepAlive(), false));
            }
            case PermissionMenuTypes.BUTTON_PERMISSION -> clearButtonMenuFields(permission);
            default -> throw new IllegalArgumentException("菜单类型参数不正确");
        }
    }

    private void trimTextFields(Permission permission) {
        permission.setParentId(StrUtil.trimToNull(permission.getParentId()));
        permission.setName(StrUtil.trimToNull(permission.getName()));
        permission.setUrl(StrUtil.trimToNull(permission.getUrl()));
        permission.setComponent(StrUtil.trimToNull(permission.getComponent()));
        permission.setComponentName(StrUtil.trimToNull(permission.getComponentName()));
        permission.setPerms(StrUtil.trimToNull(permission.getPerms()));
        permission.setIcon(StrUtil.trimToNull(permission.getIcon()));
        permission.setRedirect(StrUtil.trimToNull(permission.getRedirect()));
    }

    private void applyDefaultValues(Permission permission) {
        permission.setMenuType(Objects.requireNonNullElse(permission.getMenuType(), PermissionMenuTypes.SUB_MENU));
        permission.setSortNo(Objects.requireNonNullElse(permission.getSortNo(), 0));
        permission.setStatus(StrUtil.blankToDefault(permission.getStatus(), "1"));
        permission.setHidden(Objects.requireNonNullElse(permission.getHidden(), 0));
        permission.setHideTab(Objects.requireNonNullElse(permission.getHideTab(), 0));
        permission.setIsRoute(Objects.requireNonNullElse(permission.getIsRoute(), true));
    }

    private void clearButtonMenuFields(Permission permission) {
        permission.setUrl(null);
        permission.setComponent(null);
        permission.setComponentName(null);
        permission.setRedirect(null);
        permission.setIcon(null);
        permission.setHidden(null);
        permission.setHideTab(null);
        permission.setKeepAlive(null);
    }

    private void validatePermission(Permission permission) {
        Assert.notBlank(permission.getName(), "菜单名称不能为空");
        Assert.isTrue(PermissionMenuTypes.isValid(permission.getMenuType()), "菜单类型参数不正确");
        Assert.isTrue("0".equals(permission.getStatus()) || "1".equals(permission.getStatus()), "状态参数不正确");
        Assert.isTrue(isBinary(permission.getHidden()), "显示状态参数不正确");
        Assert.isTrue(isBinary(permission.getHideTab()), "标签页状态参数不正确");

        if (PermissionMenuTypes.isButtonPermission(permission.getMenuType())) {
            Assert.notBlank(permission.getPerms(), "按钮权限编码不能为空");
        } else {
            Assert.notBlank(permission.getUrl(), "菜单路径不能为空");
        }

        validateParent(permission);
        if (StringUtils.hasText(permission.getPerms())) {
            validateUniquePerms(permission);
        }
    }

    private boolean isBinary(Integer value) {
        return value == null || value == 0 || value == 1;
    }

    @SuppressWarnings("unchecked")
    private QueryWrapper<Permission> resolveQueryWrapper(BaseDTO<Permission> baseDTO) {
        if (baseDTO == null) {
            return new QueryWrapper<>();
        }

        QueryWrapper<Permission> wrapper = (QueryWrapper<Permission>) baseDTO.getQueryWrapper();
        if (wrapper == null) {
            wrapper = new QueryWrapper<>();
            baseDTO.setQueryWrapper(wrapper);
        }
        return wrapper;
    }

    @SuppressWarnings("unchecked")
    private QueryWrapper<Permission> resolveQueryWrapper(BasePageDTO<Permission> basePageDTO) {
        if (basePageDTO == null) {
            return new QueryWrapper<>();
        }

        QueryWrapper<Permission> wrapper = (QueryWrapper<Permission>) basePageDTO.getQueryWrapper();
        if (wrapper == null) {
            wrapper = new QueryWrapper<>();
            basePageDTO.setQueryWrapper(wrapper);
        }
        return wrapper;
    }

    private void applyPermissionOrder(QueryWrapper<Permission> wrapper) {
        wrapper.orderByAsc("sort_no")
                .orderByAsc("create_time");
    }

    private void validateUniquePerms(Permission permission) {
        LambdaQueryWrapper<Permission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Permission::getPerms, permission.getPerms())
               .eq(Permission::getDeleteFlag, 0);
        if (StringUtils.hasText(permission.getId())) {
            wrapper.ne(Permission::getId, permission.getId());
        }
        if (baseMapper.selectCount(wrapper) > 0) {
            throw new IllegalArgumentException("权限编码已存在");
        }
    }

    private void clearUnusedFields(String id, Integer menuType) {
        LambdaUpdateWrapper<Permission> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Permission::getId, id);

        switch (menuType) {
            case PermissionMenuTypes.FIRST_LEVEL_MENU -> wrapper
                    .set(Permission::getParentId, null)
                    .set(Permission::getPerms, null)
                    .set(Permission::getKeepAlive, null);
            case PermissionMenuTypes.SUB_MENU -> wrapper
                    .set(Permission::getPerms, null)
                    .set(Permission::getRedirect, null);
            case PermissionMenuTypes.BUTTON_PERMISSION -> wrapper
                    .set(Permission::getUrl, null)
                    .set(Permission::getComponent, null)
                    .set(Permission::getComponentName, null)
                    .set(Permission::getIcon, null)
                    .set(Permission::getHidden, null)
                    .set(Permission::getHideTab, null)
                    .set(Permission::getKeepAlive, null)
                    .set(Permission::getRedirect, null);
            default -> throw new IllegalArgumentException("菜单类型参数不正确");
        }
        baseMapper.update(null, wrapper);
    }

    private void validateParent(Permission permission) {
        if (!StringUtils.hasText(permission.getParentId())) {
            return;
        }
        Assert.isFalse(permission.getParentId().equals(permission.getId()), "父级菜单不能选择自身");
        if (StringUtils.hasText(permission.getId())) {
            validateParentNotSelfOrDescendant(permission.getId(), permission.getParentId());
        }
    }

    private boolean isGrantablePermissionCode(String perms) {
        return !"tenant:view".equals(perms)
                && !"tenant:edit".equals(perms)
                && !"permission:view".equals(perms)
                && !"permission:edit".equals(perms)
                && !"log:view".equals(perms)
                && !"log:edit".equals(perms);
    }

    private List<PermissionVO> filterGrantableTree(List<PermissionVO> nodes, Set<String> grantablePerms) {
        if (nodes == null || nodes.isEmpty()) {
            return new ArrayList<>();
        }

        List<PermissionVO> result = new ArrayList<>();
        for (PermissionVO node : nodes) {
            List<PermissionVO> children = filterGrantableTree(node.getChildren(), grantablePerms);
            boolean selfGrantable = !StringUtils.hasText(node.getPerms()) || grantablePerms.contains(node.getPerms());
            if (selfGrantable && (StringUtils.hasText(node.getPerms()) || !children.isEmpty())) {
                node.setChildren(children);
                result.add(node);
            }
        }
        return result;
    }
}
