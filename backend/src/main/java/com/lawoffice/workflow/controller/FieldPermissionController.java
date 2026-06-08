package com.lawoffice.workflow.controller;

import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.framework.controller.BaseController;
import com.lawoffice.workflow.entity.FieldPermission;
import com.lawoffice.workflow.req.FieldPermissionReq;
import com.lawoffice.workflow.service.IFieldPermissionService;
import com.lawoffice.workflow.vo.FieldPermissionVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workflow/admin/field-permission")
@Tag(name = "审批字段权限", description = "审批中心节点字段权限管理")
@ModuleInfo(value = "workflow:field-permission", name = "审批字段权限", description = "审批中心节点字段权限管理")
public class FieldPermissionController extends BaseController<IFieldPermissionService, FieldPermission, FieldPermissionVO, FieldPermissionReq> {

    @Autowired
    public FieldPermissionController(IFieldPermissionService service) {
        this.baseService = service;
    }
}
