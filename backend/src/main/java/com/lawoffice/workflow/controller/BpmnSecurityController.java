package com.lawoffice.workflow.controller;

import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.util.RequestContextUtils;
import com.lawoffice.system.annotation.RequiresPermission;
import com.lawoffice.workflow.req.InstanceReq;
import com.lawoffice.workflow.service.IBpmnSecurityService;
import com.lawoffice.workflow.vo.ProcessModelVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/workflow/admin/bpmn-security")
@Tag(name = "BPMN安全校验", description = "审批中心流程BPMN安全校验接口")
@ModuleInfo(value = "workflow:process", name = "BPMN安全校验", description = "审批中心流程BPMN安全校验接口")
public class BpmnSecurityController {

    private final IBpmnSecurityService bpmnSecurityService;

    @Autowired
    public BpmnSecurityController(IBpmnSecurityService bpmnSecurityService) {
        this.bpmnSecurityService = bpmnSecurityService;
    }

    @PostMapping("/validate")
    @Operation(summary = "校验流程BPMN安全")
    @RequiresPermission("workflow:process:edit")
    public BaseResult<ProcessModelVO> validate(@RequestBody InstanceReq req, HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return bpmnSecurityService.validateModel(req == null ? null : req.getId(), context);
    }
}
