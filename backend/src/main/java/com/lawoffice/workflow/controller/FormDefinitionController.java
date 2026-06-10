package com.lawoffice.workflow.controller;

import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.framework.controller.BaseController;
import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.system.annotation.RequiresPermission;
import com.lawoffice.workflow.entity.FormDefinition;
import com.lawoffice.workflow.req.FormDefinitionReq;
import com.lawoffice.workflow.service.IFormDefinitionService;
import com.lawoffice.workflow.vo.FormDefinitionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workflow/admin/form")
@Tag(name = "审批表单", description = "审批中心表单定义管理")
@ModuleInfo(value = "workflow:form", name = "审批表单", description = "审批中心表单定义管理")
public class FormDefinitionController extends BaseController<IFormDefinitionService, FormDefinition, FormDefinitionVO, FormDefinitionReq> {

    @Autowired
    public FormDefinitionController(IFormDefinitionService service) {
        this.baseService = service;
    }

    @PostMapping("/publish")
    @Operation(summary = "发布表单版本")
    @RequiresPermission("workflow:form:edit")
    public BaseResult<FormDefinitionVO> publish(@RequestBody FormDefinitionReq req,
            HttpServletRequest request,
            HttpServletResponse response) {
        BaseDTO<FormDefinition> dto = new BaseDTO<>();
        initBaseDTO(dto, request, response);
        return baseService.publish(req == null ? null : req.getId(), dto.getContext());
    }

    @PostMapping("/copy-as-draft")
    @Operation(summary = "复制表单为新草稿版本")
    @RequiresPermission("workflow:form:edit")
    public BaseResult<FormDefinitionVO> copyAsDraft(@RequestBody FormDefinitionReq req,
            HttpServletRequest request,
            HttpServletResponse response) {
        BaseDTO<FormDefinition> dto = new BaseDTO<>();
        initBaseDTO(dto, request, response);
        return baseService.copyAsDraft(req == null ? null : req.getId(), dto.getContext());
    }
}
