package com.lawoffice.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.system.entity.DepartRole;
import com.lawoffice.system.entity.SysDepart;
import com.lawoffice.system.mapper.DepartRoleMapper;
import com.lawoffice.system.mapper.DepartRolePermissionMapper;
import com.lawoffice.system.mapper.DepartRoleUserMapper;
import com.lawoffice.system.mapper.PermissionMapper;
import com.lawoffice.system.mapper.SysDepartMapper;
import com.lawoffice.system.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
