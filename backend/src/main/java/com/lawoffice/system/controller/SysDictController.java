package com.lawoffice.system.controller;

import com.lawoffice.framework.controller.BaseController;
import com.lawoffice.system.entity.SysDict;
import com.lawoffice.system.service.ISysDictService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dict")
@Tag(name = "字典管理", description = "系统字典信息管理")
public class SysDictController extends BaseController<ISysDictService, SysDict> {

    @Autowired
    public SysDictController(ISysDictService sysDictService) {
        this.baseService = sysDictService;
    }
}
