package com.lawoffice.system.controller;

import com.lawoffice.framework.controller.BaseController;
import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.system.entity.Tenant;
import com.lawoffice.system.req.TenantReq;
import com.lawoffice.system.service.ITenantService;
import com.lawoffice.system.vo.TenantVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tenant")
@Tag(name = "多租户管理", description = "系统多租户信息管理")
@ModuleInfo(value = "tenant", name = "多租户管理", description = "系统多租户信息管理")
public class TenantController extends BaseController<ITenantService, Tenant, TenantVO, TenantReq> {

    @Autowired
    public TenantController(ITenantService tenantService) {
        this.baseService = tenantService;
    }
}
