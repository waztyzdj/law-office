package com.lawoffice.system.service;

import com.lawoffice.system.vo.RoleVO;
import com.lawoffice.system.vo.SysDepartVO;
import com.lawoffice.system.vo.UserVO;

import java.util.List;

/**
 * 系统选择器数据服务。
 */
public interface ISystemPickerService {

    /**
     * 查询当前租户下可选择的有效用户。
     *
     * @return 有效用户列表
     */
    List<UserVO> listUsers();

    /**
     * 查询当前租户下可选择的组织机构。
     *
     * @return 组织机构列表
     */
    List<SysDepartVO> listDeparts();

    /**
     * 查询当前租户下可选择的系统角色。
     *
     * @return 角色列表
     */
    List<RoleVO> listRoles();

    /**
     * 查询当前租户下指定部门的有效成员。
     *
     * @param departId 部门 ID
     * @return 部门成员列表
     */
    List<UserVO> listDepartUsers(String departId);

    /**
     * 查询当前租户下指定角色的有效成员。
     *
     * @param roleId 角色 ID
     * @return 角色成员列表
     */
    List<UserVO> listRoleUsers(String roleId);
}
