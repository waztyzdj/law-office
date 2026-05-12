package com.lawoffice.system.controller;

import com.lawoffice.framework.controller.BaseController;
import com.lawoffice.system.entity.SysFiles;
import com.lawoffice.system.service.ISysFilesService;
import com.lawoffice.system.vo.SysFilesVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/files")
@Tag(name = "知识库文档管理", description = "系统知识库文档信息管理")
public class SysFilesController extends BaseController<ISysFilesService, SysFiles, SysFilesVO> {

    @Autowired
    public SysFilesController(ISysFilesService sysFilesService) {
        this.baseService = sysFilesService;
    }
}
