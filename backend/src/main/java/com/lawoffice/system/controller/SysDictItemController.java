package com.lawoffice.system.controller;

import com.lawoffice.framework.controller.BaseController;
import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.system.entity.SysDictItem;
import com.lawoffice.system.req.SysDictItemReq;
import com.lawoffice.system.service.ISysDictItemService;
import com.lawoffice.system.vo.SysDictItemVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dictItem")
@Tag(name = "字典明细管理", description = "系统字典明细信息管理")
@ModuleInfo(value = "dict-item", name = "字典明细管理", description = "系统字典明细信息管理")
public class SysDictItemController extends BaseController<ISysDictItemService, SysDictItem, SysDictItemVO, SysDictItemReq> {

    @Autowired
    public SysDictItemController(ISysDictItemService sysDictItemService) {
        this.baseService = sysDictItemService;
    }
}
