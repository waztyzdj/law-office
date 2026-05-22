package com.lawoffice.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.system.constant.PermissionMenuTypes;
import com.lawoffice.system.entity.DepartPermission;
import com.lawoffice.system.entity.Permission;
import com.lawoffice.system.entity.RolePermission;
import com.lawoffice.system.mapper.DepartPermissionMapper;
import com.lawoffice.system.mapper.PermissionMapper;
import com.lawoffice.system.mapper.RolePermissionMapper;
import com.lawoffice.system.service.IPermissionService;
import com.lawoffice.system.vo.PermissionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PermissionServiceImpl extends BaseServiceImpl<PermissionMapper, Permission, PermissionVO> implements IPermissionService {

    @Autowired
    private RolePermissionMapper rolePermissionMapper;

    @Autowired
    private DepartPermissionMapper departPermissionMapper;

    @Override
    protected void doBeforeSave(BaseDTO<Permission> saveDTO) {
        Permission permission = saveDTO.getEntity();
        if (permission == null) {
            return;
        }

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
    public List<PermissionVO> tree() {
        LambdaQueryWrapper<Permission> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Permission::getDeleteFlag, 0)
               .orderByAsc(Permission::getSortNo);
        List<PermissionVO> nodes = BeanUtil.copyToList(baseMapper.selectList(wrapper), PermissionVO.class);

        Map<String, List<PermissionVO>> childrenMap = nodes.stream()
                .filter(item -> StringUtils.hasText(item.getParentId()))
                .collect(Collectors.groupingBy(PermissionVO::getParentId));

        nodes.forEach(item -> {
            List<PermissionVO> children = childrenMap.get(item.getId());
            if (children != null && !children.isEmpty()) {
                children.sort(Comparator.comparing(
                        PermissionVO::getSortNo,
                        Comparator.nullsLast(Integer::compareTo)
                ));
                item.setChildren(children);
            }
        });

        return nodes.stream()
                .filter(item -> !StringUtils.hasText(item.getParentId()))
                .sorted(Comparator.comparing(
                        PermissionVO::getSortNo,
                        Comparator.nullsLast(Integer::compareTo)
                ))
                .collect(Collectors.toList());
    }

    @Override
    protected void doBeforeDelete(BaseDTO<Permission> deleteDTO) {
        List<String> ids = getDeleteIds(deleteDTO);
        if (ids.isEmpty()) {
            return;
        }

        LambdaQueryWrapper<Permission> childWrapper = new LambdaQueryWrapper<>();
        childWrapper.in(Permission::getParentId, ids)
                    .eq(Permission::getDeleteFlag, 0);
        if (baseMapper.selectCount(childWrapper) > 0) {
            throw new IllegalArgumentException("存在子级菜单或按钮，请先删除子级");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    protected void doAfterDelete(BaseDTO<Permission> deleteDTO) {
        List<String> ids = getDeleteIds(deleteDTO);
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
        permission.setParentId(trimToNull(permission.getParentId()));
        permission.setName(trimToNull(permission.getName()));
        permission.setUrl(trimToNull(permission.getUrl()));
        permission.setComponent(trimToNull(permission.getComponent()));
        permission.setComponentName(trimToNull(permission.getComponentName()));
        permission.setPerms(trimToNull(permission.getPerms()));
        permission.setIcon(trimToNull(permission.getIcon()));
        permission.setRedirect(trimToNull(permission.getRedirect()));

        if (permission.getMenuType() == null) {
            permission.setMenuType(PermissionMenuTypes.SUB_MENU);
        }
        if (permission.getSortNo() == null) {
            permission.setSortNo(0);
        }
        if (!StringUtils.hasText(permission.getStatus())) {
            permission.setStatus("1");
        }
        if (permission.getHidden() == null) {
            permission.setHidden(0);
        }
        if (permission.getHideTab() == null) {
            permission.setHideTab(0);
        }
        if (permission.getIsRoute() == null) {
            permission.setIsRoute(true);
        }

        if (PermissionMenuTypes.isButtonPermission(permission.getMenuType())) {
            permission.setHidden(null);
            permission.setHideTab(null);
            permission.setKeepAlive(null);
        } else {
            permission.setPerms(null);
            if (PermissionMenuTypes.isSubMenu(permission.getMenuType())) {
                permission.setRedirect(null);
            } else if (permission.getKeepAlive() == null) {
                permission.setKeepAlive(false);
            }
        }
    }

    private void validatePermission(Permission permission) {
        if (!StringUtils.hasText(permission.getName())) {
            throw new IllegalArgumentException("菜单名称不能为空");
        }
        if (!PermissionMenuTypes.isValid(permission.getMenuType())) {
            throw new IllegalArgumentException("菜单类型参数不正确");
        }
        if (!"0".equals(permission.getStatus()) && !"1".equals(permission.getStatus())) {
            throw new IllegalArgumentException("状态参数不正确");
        }
        if (permission.getHidden() != null && permission.getHidden() != 0 && permission.getHidden() != 1) {
            throw new IllegalArgumentException("显示状态参数不正确");
        }
        if (permission.getHideTab() != null && permission.getHideTab() != 0 && permission.getHideTab() != 1) {
            throw new IllegalArgumentException("标签页状态参数不正确");
        }
        if (PermissionMenuTypes.isButtonPermission(permission.getMenuType()) && !StringUtils.hasText(permission.getPerms())) {
            throw new IllegalArgumentException("按钮权限编码不能为空");
        }
        if (PermissionMenuTypes.isMenu(permission.getMenuType()) && !StringUtils.hasText(permission.getUrl())) {
            throw new IllegalArgumentException("菜单路径不能为空");
        }
        if (StringUtils.hasText(permission.getParentId()) && permission.getParentId().equals(permission.getId())) {
            throw new IllegalArgumentException("父级菜单不能选择自身");
        }
        if (StringUtils.hasText(permission.getId()) && StringUtils.hasText(permission.getParentId())) {
            validateParentNotDescendant(permission.getId(), permission.getParentId());
        }
        if (StringUtils.hasText(permission.getPerms())) {
            validateUniquePerms(permission);
        }
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

        if (PermissionMenuTypes.isButtonPermission(menuType)) {
            wrapper
                    .set(Permission::getUrl, null)
                    .set(Permission::getComponent, null)
                    .set(Permission::getComponentName, null)
                    .set(Permission::getIcon, null)
                    .set(Permission::getHidden, null)
                    .set(Permission::getHideTab, null)
                    .set(Permission::getKeepAlive, null)
                    .set(Permission::getRedirect, null);
            baseMapper.update(null, wrapper);
            return;
        }

        wrapper.set(Permission::getPerms, null);
        if (PermissionMenuTypes.isFirstLevelMenu(menuType)) {
            wrapper
                    .set(Permission::getParentId, null)
                    .set(Permission::getKeepAlive, null);
        } else if (PermissionMenuTypes.isSubMenu(menuType)) {
            wrapper.set(Permission::getRedirect, null);
        }
        baseMapper.update(null, wrapper);
    }

    private void validateParentNotDescendant(String id, String parentId) {
        String currentParentId = parentId;
        while (StringUtils.hasText(currentParentId)) {
            if (id.equals(currentParentId)) {
                throw new IllegalArgumentException("父级菜单不能选择自身或子级");
            }

            Permission parent = baseMapper.selectById(currentParentId);
            if (parent == null || parent.getDeleteFlag() != null && parent.getDeleteFlag() == 1) {
                throw new IllegalArgumentException("父级菜单不存在或已被删除");
            }
            currentParentId = parent.getParentId();
        }
    }

    private List<String> getDeleteIds(BaseDTO<Permission> deleteDTO) {
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

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
