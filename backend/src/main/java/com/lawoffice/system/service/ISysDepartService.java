package com.lawoffice.system.service;

import com.lawoffice.framework.service.ITreeService;
import com.lawoffice.system.entity.DepartRole;
import com.lawoffice.system.entity.Permission;
import com.lawoffice.system.entity.SysDepart;
import com.lawoffice.system.req.DepartLeaderReq;
import com.lawoffice.system.req.DepartMemberRelationReq;
import com.lawoffice.system.vo.SysDepartVO;
import com.lawoffice.system.vo.DepartPermissionSourceVO;
import com.lawoffice.system.vo.DepartMemberRelationVO;
import com.lawoffice.system.vo.UserVO;

import java.util.List;

public interface ISysDepartService extends ITreeService<SysDepart, SysDepartVO> {

    /**
     * 为部门分配角色
     * @param departId 部门ID
     * @param roleIds 角色ID列表
     */
    void assignRoles(String departId, List<String> roleIds);

    /**
     * 获取部门的角色列表
     * @param departId 部门ID
     * @return 角色列表
     */
    List<DepartRole> getDepartRoles(String departId);

    /**
     * 获取部门角色 ID 列表
     * @param departId 部门ID
     * @return 部门角色 ID 列表
     */
    List<String> getDepartRoleIds(String departId);

    /**
     * 移除部门的指定角色
     * @param departId 部门ID
     * @param roleIds 角色ID列表
     */
    void removeRoles(String departId, List<String> roleIds);

    /**
     * 为部门分配权限
     * @param departId 部门ID
     * @param permissionIds 权限ID列表
     */
    void assignPermissions(String departId, List<String> permissionIds);

    /**
     * 获取部门的权限列表
     * @param departId 部门ID
     * @return 权限列表
     */
    List<Permission> getDepartPermissions(String departId);

    /**
     * 获取部门权限 ID 列表
     * @param departId 部门ID
     * @return 权限 ID 列表
     */
    List<String> getDepartPermissionIds(String departId);

    /**
     * 获取部门权限来源列表。
     * @param departId 部门ID
     * @return 权限来源列表
     */
    List<DepartPermissionSourceVO> getDepartPermissionSources(String departId);

    /**
     * 移除部门的指定权限
     * @param departId 部门ID
     * @param permissionIds 权限ID列表
     */
    void removePermissions(String departId, List<String> permissionIds);

    /**
     * 覆盖保存部门成员。
     * <p>
     * 部门成员默认拥有本部门默认角色。
     *
     * @param departId 部门ID
     * @param userIds 用户 ID 列表
     */
    void assignUsers(String departId, List<String> userIds);

    /**
     * 获取部门成员列表。
     * @param departId 部门ID
     * @return 用户列表
     */
    List<UserVO> getDepartUsers(String departId);

    /**
     * 获取部门成员 ID 列表。
     * @param departId 部门ID
     * @return 用户 ID 列表
     */
    List<String> getDepartUserIds(String departId);

    /**
     * 查询部门成员组织关系。
     *
     * @param departId 部门 ID
     * @return 部门成员组织关系列表
     */
    List<DepartMemberRelationVO> getDepartMemberRelations(String departId);

    /**
     * 覆盖保存部门成员组织关系。
     * <p>
     * 该接口只维护已有部门成员的主部门、部门负责人和直属上级，不新增或移除部门成员。
     *
     * @param req 保存请求
     */
    void saveDepartMemberRelations(DepartMemberRelationReq req);

    /**
     * 查询部门负责人。
     *
     * @param departId 部门 ID
     * @return 部门负责人列表，当前规则下最多一条
     */
    List<DepartMemberRelationVO> getDepartLeaders(String departId);

    /**
     * 保存部门唯一负责人。
     *
     * @param req 保存请求，userId 为空时清空负责人
     */
    void saveDepartLeader(DepartLeaderReq req);
}
