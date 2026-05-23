package com.lawoffice.system.controller;

import com.lawoffice.framework.controller.BaseController;
import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.system.entity.SysDict;
import com.lawoffice.system.req.SysDictReq;
import com.lawoffice.system.service.ISysDictService;
import com.lawoffice.system.vo.DictOptionVO;
import com.lawoffice.system.vo.SysDictVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/dict")
@Tag(name = "字典管理", description = "系统字典信息管理")
@ModuleInfo(value = "dict", name = "字典管理", description = "系统字典信息管理")
public class SysDictController extends BaseController<ISysDictService, SysDict, SysDictVO, SysDictReq> {

    @Autowired
    public SysDictController(ISysDictService sysDictService) {
        this.baseService = sysDictService;
    }

    @GetMapping("/options/{dictCode}")
    public BaseResult<List<DictOptionVO>> options(@PathVariable String dictCode) {
        return baseService.listOptionsByCode(dictCode);
    }
}
