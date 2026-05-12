package com.lawoffice.system.controller;

import com.lawoffice.framework.controller.BaseController;
import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.system.entity.Role;
import com.lawoffice.system.req.RoleReq;
import com.lawoffice.system.service.IRoleService;
import com.lawoffice.system.vo.RoleVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/role")
@Tag(name = "角色管理", description = "系统角色信息管理")
@ModuleInfo(value = "role", name = "角色管理", description = "系统角色信息管理")
public class RoleController extends BaseController<IRoleService, Role, RoleVO, RoleReq> {

    @Autowired
    public RoleController(IRoleService roleService) {
        this.baseService = roleService;
    }
}
