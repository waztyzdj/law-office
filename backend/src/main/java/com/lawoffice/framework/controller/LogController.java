package com.lawoffice.framework.controller;

import com.lawoffice.framework.annotation.AutoLog;
import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.framework.dto.BasePageDTO;
import com.lawoffice.framework.entity.SysLog;
import com.lawoffice.framework.enums.LogType;
import com.lawoffice.framework.enums.OperateType;
import com.lawoffice.framework.req.BasePageReq;
import com.lawoffice.framework.req.BaseQueryReq;
import com.lawoffice.framework.req.BaseReq;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.service.ILogService;
import com.lawoffice.framework.util.QueryWrapperBuilderUtils;
import com.lawoffice.framework.util.RequestContextUtils;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.framework.vo.SysLogVO;
import com.lawoffice.system.annotation.RequiresPermission;
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
@RequestMapping("/log")
@Tag(name = "日志管理", description = "系统日志信息管理")
@ModuleInfo(value = "log", name = "日志管理", description = "系统日志信息管理")
public class LogController {

    @Autowired
    private ILogService logService;

    @PostMapping("/list")
    @RequiresPermission("log:view")
    @Operation(summary = "列表查询", description = "查询日志列表（不分页）")
    public BaseResult<List<SysLogVO>> list(
            @RequestBody(required = false) BaseQueryReq req,
            HttpServletRequest request,
            HttpServletResponse response) {
        BaseDTO<SysLog> baseDTO = buildBaseDTO(request, response);
        if (req != null) {
            baseDTO.setQueryWrapper(QueryWrapperBuilderUtils.build(req));
        }
        return logService.list(baseDTO);
    }

    @PostMapping("/page")
    @RequiresPermission("log:view")
    @Operation(summary = "分页查询", description = "分页查询系统日志列表")
    public BaseResult<PageVO<SysLogVO>> page(
            @RequestBody(required = false) BasePageReq req,
            HttpServletRequest request,
            HttpServletResponse response) {
        BasePageDTO<SysLog> basePageDTO = new BasePageDTO<>();
        if (req != null) {
            basePageDTO.setPageNum(req.getPageNum());
            basePageDTO.setPageSize(req.getPageSize());
            basePageDTO.setQueryWrapper(QueryWrapperBuilderUtils.build(req));
        }

        initBaseDTO(basePageDTO, request, response);
        return logService.page(basePageDTO);
    }

    @PostMapping("/getById")
    @RequiresPermission("log:view")
    @Operation(summary = "根据ID查询", description = "根据ID查询单条日志")
    public BaseResult<SysLogVO> getById(
            @RequestBody BaseReq req,
            HttpServletRequest request,
            HttpServletResponse response) {
        BaseDTO<SysLog> baseDTO = buildBaseDTO(request, response);
        baseDTO.setId(req != null ? req.getId() : null);
        return logService.getById(baseDTO);
    }

    @PostMapping("/delete")
    @RequiresPermission("log:edit")
    @AutoLog(value = "删除日志", logType = LogType.OPERATION, operateType = OperateType.DELETE)
    @Operation(summary = "删除日志", description = "逻辑删除单条日志")
    public BaseResult<Void> delete(
            @RequestBody BaseReq req,
            HttpServletRequest request,
            HttpServletResponse response) {
        BaseDTO<SysLog> baseDTO = buildBaseDTO(request, response);
        baseDTO.setId(req != null ? req.getId() : null);
        return logService.delete(baseDTO);
    }

    @PostMapping("/batchDelete")
    @RequiresPermission("log:edit")
    @AutoLog(value = "批量删除日志", logType = LogType.OPERATION, operateType = OperateType.BATCH_DELETE)
    @Operation(summary = "批量删除日志", description = "逻辑删除多条日志")
    public BaseResult<Void> batchDelete(
            @RequestBody List<String> ids,
            HttpServletRequest request,
            HttpServletResponse response) {
        BaseDTO<SysLog> baseDTO = buildBaseDTO(request, response);
        baseDTO.setDeleteIds(ids);
        return logService.batchDelete(baseDTO);
    }

    private BaseDTO<SysLog> buildBaseDTO(HttpServletRequest request, HttpServletResponse response) {
        BaseDTO<SysLog> baseDTO = new BaseDTO<>();
        initBaseDTO(baseDTO, request, response);
        return baseDTO;
    }

    private void initBaseDTO(BaseDTO<SysLog> baseDTO, HttpServletRequest request, HttpServletResponse response) {
        baseDTO.setRequest(request);
        baseDTO.setResponse(response);
        baseDTO.setContext(RequestContextUtils.buildContext(request));
    }
}
