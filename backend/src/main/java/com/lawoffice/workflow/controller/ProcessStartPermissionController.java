package com.lawoffice.workflow.controller;

import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.framework.controller.BaseController;
import com.lawoffice.workflow.entity.ProcessStartPermission;
import com.lawoffice.workflow.req.ProcessStartPermissionReq;
import com.lawoffice.workflow.service.IProcessStartPermissionService;
import com.lawoffice.workflow.vo.ProcessStartPermissionVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workflow/admin/start-permission")
@Tag(name = "流程发起权限", description = "审批中心流程发起权限管理")
@ModuleInfo(value = "workflow:start-permission", name = "流程发起权限", description = "审批中心流程发起权限管理")
public class ProcessStartPermissionController extends BaseController<IProcessStartPermissionService, ProcessStartPermission, ProcessStartPermissionVO, ProcessStartPermissionReq> {

    @Autowired
    public ProcessStartPermissionController(IProcessStartPermissionService service) {
        this.baseService = service;
    }
}
