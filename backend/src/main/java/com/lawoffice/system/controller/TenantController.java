package com.lawoffice.system.controller;

import com.lawoffice.framework.controller.BaseController;
import com.lawoffice.system.entity.Tenant;
import com.lawoffice.system.service.ITenantService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tenant")
@Tag(name = "多租户管理", description = "系统多租户信息管理")
public class TenantController extends BaseController<ITenantService, Tenant> {

    @Autowired
    public TenantController(ITenantService tenantService) {
        this.baseService = tenantService;
    }
}
