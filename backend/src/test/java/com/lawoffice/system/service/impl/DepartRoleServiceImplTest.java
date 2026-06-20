package com.lawoffice.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.system.entity.DepartRole;
import com.lawoffice.system.entity.DepartRoleUser;
import com.lawoffice.system.entity.SysDepart;
import com.lawoffice.system.entity.UserDepart;
import com.lawoffice.system.mapper.DepartRoleMapper;
import com.lawoffice.system.mapper.DepartRolePermissionMapper;
import com.lawoffice.system.mapper.DepartRoleUserMapper;
import com.lawoffice.system.mapper.PermissionMapper;
import com.lawoffice.system.mapper.SysDepartMapper;
import com.lawoffice.system.mapper.UserDepartMapper;
import com.lawoffice.system.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DepartRoleServiceImplTest {

    @Mock
    private DepartRoleMapper departRoleMapper;
    @Mock
    private DepartRolePermissionMapper departRolePermissionMapper;
    @Mock
    private DepartRoleUserMapper departRoleUserMapper;
    @Mock
    private PermissionMapper permissionMapper;
    @Mock
    private UserMapper userMapper;
    @Mock
    private UserDepartMapper userDepartMapper;
    @Mock
    private SysDepartMapper sysDepartMapper;

    private DepartRoleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new DepartRoleServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", departRoleMapper);
        ReflectionTestUtils.setField(service, "departRolePermissionMapper", departRolePermissionMapper);
        ReflectionTestUtils.setField(service, "departRoleUserMapper", departRoleUserMapper);
        ReflectionTestUtils.setField(service, "permissionMapper", permissionMapper);
        ReflectionTestUtils.setField(service, "userMapper", userMapper);
        ReflectionTestUtils.setField(service, "userDepartMapper", userDepartMapper);
        ReflectionTestUtils.setField(service, "sysDepartMapper", sysDepartMapper);
    }

    @Test
    void shouldRejectDefaultDepartRoleUpdate() {
        SysDepart depart = buildDepart();
        DepartRole oldRole = buildDefaultRole();
        DepartRole requestRole = buildDefaultRole();
        requestRole.setRoleName("修改后的名称");

        when(sysDepartMapper.selectById("depart-1")).thenReturn(depart);
        when(departRoleMapper.selectById("role-1")).thenReturn(oldRole);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.doBeforeSave(buildSaveDTO(requestRole))
        );

        assertEquals("部门默认角色不能修改", exception.getMessage());
    }

    @Test
    void shouldRejectDefaultDepartRoleDelete() {
        DepartRole role = buildDefaultRole();
        when(departRoleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(role));
        when(sysDepartMapper.selectById("depart-1")).thenReturn(buildDepart());

        BaseDTO<DepartRole> deleteDTO = new BaseDTO<>();
        deleteDTO.setId("role-1");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.doBeforeDelete(deleteDTO)
        );

        assertEquals("部门默认角色不能删除: 测试部门默认角色", exception.getMessage());
    }

    @Test
    void shouldRejectDefaultDepartRoleUserAssignment() {
        when(departRoleMapper.selectById("role-1")).thenReturn(buildDefaultRole());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.assignUsers("role-1", List.of("user-1"))
        );

        assertEquals("部门默认角色用户由部门成员自动维护", exception.getMessage());
    }

    @Test
    void shouldAssignUsersByDiffWithoutDeletingUnchangedRelations() {
        DepartRole role = buildCustomRole();
        role.setTenantId("tenant-1");

        when(departRoleMapper.selectById("role-2")).thenReturn(role);
        when(sysDepartMapper.selectById("depart-1")).thenReturn(buildDepart());
        when(sysDepartMapper.selectList(any(Wrapper.class))).thenReturn(List.of(buildDepart()));
        when(userDepartMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(buildUserDepart("user-1"), buildUserDepart("user-2"), buildUserDepart("user-3")));
        when(departRoleUserMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(buildRoleUser("user-1"), buildRoleUser("user-2")));

        service.assignUsers("role-2", List.of("user-1", "user-2", "user-3", "user-3", ""));

        verify(departRoleUserMapper, never()).update(any(), any());
        ArgumentCaptor<DepartRoleUser> insertCaptor = ArgumentCaptor.forClass(DepartRoleUser.class);
        verify(departRoleUserMapper).insert(insertCaptor.capture());
        DepartRoleUser inserted = insertCaptor.getValue();
        assertEquals("role-2", inserted.getDroleId());
        assertEquals("user-3", inserted.getUserId());
        assertEquals("tenant-1", inserted.getTenantId());
    }

    @Test
    void shouldRejectDepartRoleUserOutsideDepartScope() {
        DepartRole role = buildCustomRole();
        role.setTenantId("tenant-1");

        when(departRoleMapper.selectById("role-2")).thenReturn(role);
        when(sysDepartMapper.selectById("depart-1")).thenReturn(buildDepart());
        when(sysDepartMapper.selectList(any(Wrapper.class))).thenReturn(List.of(buildDepart()));
        when(userDepartMapper.selectList(any(Wrapper.class)))
                .thenReturn(List.of(buildUserDepart("user-1")));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.assignUsers("role-2", List.of("user-1", "user-outside"))
        );

        assertEquals("部门角色成员只能选择本部门及下级部门人员", exception.getMessage());
    }

    @Test
    void shouldRejectCustomDepartRoleCodeWithProtectedPrefix() {
        DepartRole role = buildCustomRole();
        role.setId(null);
        role.setRoleCode("DEPART_CUSTOM");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.doBeforeSave(buildSaveDTO(role))
        );

        assertEquals("自定义部门角色编码不能以 DEPART 或 ADMIN 开头", exception.getMessage());
    }

    @Test
    void shouldKeepRoleCodeWhenUpdateCustomDepartRole() {
        DepartRole oldRole = buildCustomRole();
        DepartRole requestRole = buildCustomRole();
        requestRole.setRoleCode("NEW_CODE");

        when(sysDepartMapper.selectById("depart-1")).thenReturn(buildDepart());
        when(departRoleMapper.selectById("role-2")).thenReturn(oldRole);
        when(departRoleMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        service.doBeforeSave(buildSaveDTO(requestRole));

        assertEquals("CUSTOM_ROLE", requestRole.getRoleCode());
    }

    private DepartRole buildDefaultRole() {
        DepartRole role = new DepartRole();
        role.setId("role-1");
        role.setDepartId("depart-1");
        role.setRoleName("测试部门默认角色");
        role.setRoleCode("DEPART_tenant-1_D001");
        role.setDescription("部门默认角色");
        role.setDeleteFlag(0);
        return role;
    }

    private DepartRole buildCustomRole() {
        DepartRole role = new DepartRole();
        role.setId("role-2");
        role.setDepartId("depart-1");
        role.setRoleName("自定义部门角色");
        role.setRoleCode("CUSTOM_ROLE");
        role.setDescription("自定义角色");
        role.setDeleteFlag(0);
        return role;
    }

    private DepartRoleUser buildRoleUser(String userId) {
        DepartRoleUser roleUser = new DepartRoleUser();
        roleUser.setDroleId("role-2");
        roleUser.setUserId(userId);
        roleUser.setDeleteFlag(0);
        return roleUser;
    }

    private UserDepart buildUserDepart(String userId) {
        UserDepart userDepart = new UserDepart();
        userDepart.setDepId("depart-1");
        userDepart.setUserId(userId);
        userDepart.setTenantId("tenant-1");
        userDepart.setDeleteFlag(0);
        return userDepart;
    }

    private SysDepart buildDepart() {
        SysDepart depart = new SysDepart();
        depart.setId("depart-1");
        depart.setTenantId("tenant-1");
        depart.setOrgCode("D001");
        depart.setDeleteFlag(0);
        return depart;
    }

    private BaseDTO<DepartRole> buildSaveDTO(DepartRole role) {
        BaseDTO<DepartRole> saveDTO = new BaseDTO<>();
        saveDTO.setEntity(role);
        return saveDTO;
    }
}
