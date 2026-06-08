package com.lawoffice.workflow.controller;

import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.framework.controller.BaseController;
import com.lawoffice.workflow.entity.ProcessCategory;
import com.lawoffice.workflow.req.ProcessCategoryReq;
import com.lawoffice.workflow.service.IProcessCategoryService;
import com.lawoffice.workflow.vo.ProcessCategoryVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workflow/admin/category")
@Tag(name = "审批流程分类", description = "审批中心流程分类管理")
@ModuleInfo(value = "workflow:category", name = "审批流程分类", description = "审批中心流程分类管理")
public class ProcessCategoryController extends BaseController<IProcessCategoryService, ProcessCategory, ProcessCategoryVO, ProcessCategoryReq> {

    @Autowired
    public ProcessCategoryController(IProcessCategoryService service) {
        this.baseService = service;
    }
}
