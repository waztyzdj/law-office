package com.lawoffice.workflow.controller;

import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.framework.controller.BaseController;
import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.framework.dto.BasePageDTO;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.req.BasePageReq;
import com.lawoffice.framework.util.QueryWrapperBuilderUtils;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.system.annotation.RequiresPermission;
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

import java.util.List;

@RestController
@RequestMapping("/workflow/admin/process")
@Tag(name = "审批流程", description = "审批中心流程模型管理")
@ModuleInfo(value = "workflow:process", name = "审批流程", description = "审批中心流程模型管理")
public class ProcessModelController extends BaseController<IProcessModelService, ProcessModel, ProcessModelVO, ProcessModelReq> {

    @Autowired
    public ProcessModelController(IProcessModelService service) {
        this.baseService = service;
    }

    @PostMapping("/latest-page")
    @Operation(summary = "分页查询流程最新版本")
    public BaseResult<PageVO<ProcessModelVO>> latestPage(@RequestBody(required = false) BasePageReq req,
            HttpServletRequest request,
            HttpServletResponse response) {
        BasePageDTO<ProcessModel> basePageDTO = new BasePageDTO<>();
        if (req != null) {
            basePageDTO.setPageNum(req.getPageNum());
            basePageDTO.setPageSize(req.getPageSize());
            basePageDTO.setQueryWrapper(QueryWrapperBuilderUtils.build(req));
        }
        initBaseDTO(basePageDTO, request, response);
        return baseService.pageLatest(basePageDTO);
    }

    @PostMapping("/history")
    @Operation(summary = "查询流程历史版本")
    public BaseResult<List<ProcessModelVO>> history(@RequestBody ProcessModelReq req,
            HttpServletRequest request,
            HttpServletResponse response) {
        BaseDTO<ProcessModel> dto = new BaseDTO<>();
        initBaseDTO(dto, request, response);
        return baseService.listHistory(req == null ? null : req.getId(), dto.getContext());
    }

    @PostMapping("/publish")
    @Operation(summary = "发布流程版本")
    @RequiresPermission("workflow:process:edit")
    public BaseResult<ProcessModelVO> publish(@RequestBody ProcessModelReq req,
            HttpServletRequest request,
            HttpServletResponse response) {
        BaseDTO<ProcessModel> dto = new BaseDTO<>();
        initBaseDTO(dto, request, response);
        return baseService.publish(req == null ? null : req.getId(), dto.getContext());
    }

    @PostMapping("/copy-as-draft")
    @Operation(summary = "复制流程为新草稿版本")
    @RequiresPermission("workflow:process:edit")
    public BaseResult<ProcessModelVO> copyAsDraft(@RequestBody ProcessModelReq req,
            HttpServletRequest request,
            HttpServletResponse response) {
        BaseDTO<ProcessModel> dto = new BaseDTO<>();
        initBaseDTO(dto, request, response);
        return baseService.copyAsDraft(req == null ? null : req.getId(), dto.getContext());
    }
}
