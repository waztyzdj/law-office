package com.lawoffice.system.controller;

import com.lawoffice.framework.controller.BaseController;
import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.system.entity.Permission;
import com.lawoffice.system.req.PermissionReq;
import com.lawoffice.system.service.IPermissionService;
import com.lawoffice.system.vo.PermissionVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/permission")
@Tag(name = "菜单权限管理", description = "系统菜单权限信息管理")
@ModuleInfo(value = "permission", name = "菜单权限管理", description = "系统菜单权限信息管理")
public class PermissionController extends BaseController<IPermissionService, Permission, PermissionVO, PermissionReq> {

    @Autowired
    public PermissionController(IPermissionService permissionService) {
        this.baseService = permissionService;
    }
}
