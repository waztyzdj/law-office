package com.lawoffice.system.controller;

import com.lawoffice.framework.controller.BaseController;
import com.lawoffice.system.entity.SysDictItem;
import com.lawoffice.system.service.ISysDictItemService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dictItem")
@Tag(name = "字典明细管理", description = "系统字典明细信息管理")
public class SysDictItemController extends BaseController<ISysDictItemService, SysDictItem> {

    @Autowired
    public SysDictItemController(ISysDictItemService sysDictItemService) {
        this.baseService = sysDictItemService;
    }
}
