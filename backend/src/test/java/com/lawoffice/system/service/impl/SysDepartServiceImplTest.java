package com.lawoffice.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.system.entity.DepartRole;
import com.lawoffice.system.entity.SysDepart;
import com.lawoffice.system.mapper.DepartPermissionMapper;
import com.lawoffice.system.mapper.DepartRoleMapper;
import com.lawoffice.system.mapper.DepartRolePermissionMapper;
import com.lawoffice.system.mapper.DepartRoleUserMapper;
import com.lawoffice.system.mapper.PermissionMapper;
import com.lawoffice.system.mapper.SysDepartMapper;
import com.lawoffice.system.mapper.UserDepartMapper;
import com.lawoffice.system.mapper.UserMapper;
import com.lawoffice.system.vo.SysDepartVO;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysDepartServiceImplTest {

    @Mock
    private SysDepartMapper sysDepartMapper;
    @Mock
    private DepartRoleMapper departRoleMapper;
    @Mock
    private DepartPermissionMapper departPermissionMapper;
    @Mock
    private DepartRolePermissionMapper departRolePermissionMapper;
    @Mock
    private DepartRoleUserMapper departRoleUserMapper;
    @Mock
    private PermissionMapper permissionMapper;
    @Mock
    private UserDepartMapper userDepartMapper;
    @Mock
    private UserMapper userMapper;

    private SysDepartServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SysDepartServiceImpl(
                departRoleMapper,
                departPermissionMapper,
                departRolePermissionMapper,
                departRoleUserMapper,
                permissionMapper,
                userDepartMapper,
                userMapper
        );
        ReflectionTestUtils.setField(service, "baseMapper", sysDepartMapper);
    }

    @Test
    void shouldRejectSelfParentWhenSaveDepart() {
        SysDepart depart = buildDepart();
        depart.setId("depart-1");
        depart.setParentId("depart-1");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.doBeforeSave(buildSaveDTO(depart))
        );

        assertEquals("父节点不能选择自身", exception.getMessage());
    }

    @Test
    void shouldRejectDuplicateOrgCodeWhenSaveDepart() {
        SysDepart depart = buildDepart();
        when(sysDepartMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.doBeforeSave(buildSaveDTO(depart))
        );

        assertEquals("机构编码已存在", exception.getMessage());
    }

    @Test
    void shouldCreateDefaultDepartRoleWithTenantAndOrgCode() {
        SysDepart depart = buildDepart();
        depart.setId("depart-1");
        depart.setTenantId("tenant-1");
        SysDepartVO vo = new SysDepartVO();
        vo.setId("depart-1");

        when(sysDepartMapper.selectById("depart-1")).thenReturn(depart);
        when(departRoleMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(userDepartMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

        service.doAfterSave(buildSaveDTO(depart), vo);

        ArgumentCaptor<DepartRole> captor = ArgumentCaptor.forClass(DepartRole.class);
        verify(departRoleMapper).insert(captor.capture());
        assertEquals("DEPART_tenant-1_D001", captor.getValue().getRoleCode());
        assertEquals("测试部门默认角色", captor.getValue().getRoleName());
    }

    @Test
    void shouldLogicDeleteDepartRelationsAfterDeleteDepart() {
        BaseDTO<SysDepart> deleteDTO = new BaseDTO<>();
        deleteDTO.setId("depart-1");
        deleteDTO.setContext(RequestContext.builder().username("tester").build());

        DepartRole role = new DepartRole();
        role.setId("role-1");
        when(departRoleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(role));

        service.doAfterDelete(deleteDTO);

        verify(userDepartMapper).update(argThat(entity ->
                entity.getDeleteFlag() == 1 && "tester".equals(entity.getDeleteBy())
        ), any(LambdaUpdateWrapper.class));
        verify(departRoleUserMapper).update(argThat(entity ->
                entity.getDeleteFlag() == 1 && "tester".equals(entity.getDeleteBy())
        ), any(LambdaUpdateWrapper.class));
        verify(departRolePermissionMapper).update(argThat(entity ->
                entity.getDeleteFlag() == 1 && "tester".equals(entity.getDeleteBy())
        ), any(LambdaUpdateWrapper.class));
        verify(departPermissionMapper).update(argThat(entity ->
                entity.getDeleteFlag() == 1 && "tester".equals(entity.getDeleteBy())
        ), any(LambdaUpdateWrapper.class));
        verify(departRoleMapper).update(argThat(entity ->
                entity.getDeleteFlag() == 1 && "tester".equals(entity.getDeleteBy())
        ), any(LambdaUpdateWrapper.class));
    }

    private SysDepart buildDepart() {
        SysDepart depart = new SysDepart();
        depart.setDepartName("测试部门");
        depart.setOrgCode("D001");
        depart.setOrgType("5");
        depart.setStatus("1");
        return depart;
    }

    private BaseDTO<SysDepart> buildSaveDTO(SysDepart depart) {
        BaseDTO<SysDepart> saveDTO = new BaseDTO<>();
        saveDTO.setEntity(depart);
        saveDTO.setContext(RequestContext.builder().tenantId("tenant-1").username("tester").build());
        return saveDTO;
    }
}
