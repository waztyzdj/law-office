package com.lawoffice.system.controller;

import com.lawoffice.framework.controller.BaseController;
import com.lawoffice.system.entity.SysCategory;
import com.lawoffice.system.service.ISysCategoryService;
import com.lawoffice.system.vo.SysCategoryVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/category")
@Tag(name = "通用类型管理", description = "系统通用类型信息管理")
public class SysCategoryController extends BaseController<ISysCategoryService, SysCategory, SysCategoryVO> {

    @Autowired
    public SysCategoryController(ISysCategoryService sysCategoryService) {
        this.baseService = sysCategoryService;
    }
}
