package com.lawoffice.system.controller;

import com.lawoffice.framework.controller.BaseController;
import com.lawoffice.system.entity.SysDepart;
import com.lawoffice.system.service.ISysDepartService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/depart")
@Tag(name = "组织机构管理", description = "系统组织机构信息管理")
public class SysDepartController extends BaseController<ISysDepartService, SysDepart> {

    @Autowired
    public SysDepartController(ISysDepartService sysDepartService) {
        this.baseService = sysDepartService;
    }
}
