package com.lawoffice.workflow.controller;

import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.framework.controller.BaseController;
import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.workflow.entity.ProcessModel;
import com.lawoffice.workflow.req.ProcessModelReq;
import com.lawoffice.workflow.service.IProcessModelService;
import com.lawoffice.workflow.vo.ProcessModelVO;
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
@RequestMapping("/workflow/admin/process")
@Tag(name = "审批流程", description = "审批中心流程模型管理")
@ModuleInfo(value = "workflow:process", name = "审批流程", description = "审批中心流程模型管理")
public class ProcessModelController extends BaseController<IProcessModelService, ProcessModel, ProcessModelVO, ProcessModelReq> {

    @Autowired
    public ProcessModelController(IProcessModelService service) {
        this.baseService = service;
    }

    @PostMapping("/publish")
    @Operation(summary = "发布流程版本")
    public BaseResult<ProcessModelVO> publish(@RequestBody ProcessModelReq req,
            HttpServletRequest request,
            HttpServletResponse response) {
        BaseDTO<ProcessModel> dto = new BaseDTO<>();
        initBaseDTO(dto, request, response);
        return baseService.publish(req == null ? null : req.getId(), dto.getContext());
    }

    @PostMapping("/copy-as-draft")
    @Operation(summary = "复制流程为新草稿版本")
    public BaseResult<ProcessModelVO> copyAsDraft(@RequestBody ProcessModelReq req,
            HttpServletRequest request,
            HttpServletResponse response) {
        BaseDTO<ProcessModel> dto = new BaseDTO<>();
        initBaseDTO(dto, request, response);
        return baseService.copyAsDraft(req == null ? null : req.getId(), dto.getContext());
    }
}
