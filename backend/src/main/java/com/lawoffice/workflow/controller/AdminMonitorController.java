package com.lawoffice.workflow.controller;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.util.RequestContextUtils;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.system.annotation.RequiresPermission;
import com.lawoffice.workflow.req.AdminMonitorActionReq;
import com.lawoffice.workflow.req.AdminMonitorPageReq;
import com.lawoffice.workflow.service.IAdminMonitorService;
import com.lawoffice.workflow.vo.AdminMonitorDetailVO;
import com.lawoffice.workflow.vo.AdminMonitorInstanceVO;
import com.lawoffice.workflow.vo.AdminOperationRecordVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/workflow/admin/monitor")
@Tag(name = "流程监控", description = "审批中心流程监控和异常流程维护")
public class AdminMonitorController {

    private final IAdminMonitorService adminMonitorService;

    public AdminMonitorController(IAdminMonitorService adminMonitorService) {
        this.adminMonitorService = adminMonitorService;
    }

    @PostMapping("/page")
    @Operation(summary = "分页查询流程监控列表")
    @RequiresPermission("workflow:monitor:view")
    public BaseResult<PageVO<AdminMonitorInstanceVO>> page(@RequestBody(required = false) AdminMonitorPageReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return adminMonitorService.page(req, context);
    }

    @PostMapping("/detail")
    @Operation(summary = "查询流程监控详情")
    @RequiresPermission("workflow:monitor:view")
    public BaseResult<AdminMonitorDetailVO> detail(@RequestBody AdminMonitorActionReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return adminMonitorService.detail(req == null ? null : req.getProcessInstanceId(), context);
    }

    @PostMapping("/reassign")
    @Operation(summary = "管理员改派当前待办")
    @RequiresPermission("workflow:monitor:manage")
    public BaseResult<AdminOperationRecordVO> reassign(@Valid @RequestBody AdminMonitorActionReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return adminMonitorService.reassign(req, context);
    }

    @PostMapping("/terminate")
    @Operation(summary = "管理员终止流程")
    @RequiresPermission("workflow:monitor:manage")
    public BaseResult<AdminOperationRecordVO> terminate(@Valid @RequestBody AdminMonitorActionReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return adminMonitorService.terminate(req, context);
    }

    @PostMapping("/resend-notice")
    @Operation(summary = "补发当前待办通知")
    @RequiresPermission("workflow:monitor:manage")
    public BaseResult<AdminOperationRecordVO> resendNotice(@Valid @RequestBody AdminMonitorActionReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return adminMonitorService.resendNotice(req, context);
    }

    @PostMapping("/operation-records")
    @Operation(summary = "查询流程监控维护记录")
    @RequiresPermission("workflow:monitor:view")
    public BaseResult<List<AdminOperationRecordVO>> operationRecords(@RequestBody AdminMonitorActionReq req,
            HttpServletRequest request) {
        RequestContext context = RequestContextUtils.buildContext(request);
        return adminMonitorService.listOperationRecords(req == null ? null : req.getProcessInstanceId(), context);
    }
}
