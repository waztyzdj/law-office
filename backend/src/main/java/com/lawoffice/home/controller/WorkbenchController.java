package com.lawoffice.home.controller;

import com.lawoffice.framework.req.BaseReq;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.util.RequestContextUtils;
import com.lawoffice.home.constant.HomeWorkbenchConstants;
import com.lawoffice.home.req.WorkbenchCardDataReq;
import com.lawoffice.home.req.WorkbenchLayoutSaveReq;
import com.lawoffice.home.req.WorkbenchQuickEntryListReq;
import com.lawoffice.home.req.WorkbenchQuickEntryReq;
import com.lawoffice.home.service.IWorkbenchQuickEntryService;
import com.lawoffice.home.service.IWorkbenchUserCardService;
import com.lawoffice.home.vo.WorkbenchCardDataVO;
import com.lawoffice.home.vo.WorkbenchLayoutVO;
import com.lawoffice.home.vo.WorkbenchQuickEntryListVO;
import com.lawoffice.home.vo.WorkbenchQuickEntryVO;
import com.lawoffice.system.annotation.RequiresPermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/home/workbench")
@RequiredArgsConstructor
@Tag(name = "工作台", description = "工作台用户端能力")
public class WorkbenchController {

    private final IWorkbenchUserCardService userCardService;
    private final IWorkbenchQuickEntryService quickEntryService;

    @PostMapping("/layout")
    @RequiresPermission(HomeWorkbenchConstants.PERMISSION_WORKBENCH_VIEW)
    @Operation(summary = "查询我的工作台布局", description = "查询当前用户可见卡片、隐藏卡片和个性化布局")
    public BaseResult<WorkbenchLayoutVO> layout(HttpServletRequest request) {
        return BaseResult.success(userCardService.getCurrentLayout(RequestContextUtils.buildContext(request)));
    }

    @PostMapping("/layout/save")
    @RequiresPermission(HomeWorkbenchConstants.PERMISSION_WORKBENCH_VIEW)
    @Operation(summary = "保存我的工作台布局", description = "保存当前用户卡片显隐、栅格位置、尺寸和用户级配置")
    public BaseResult<Void> saveLayout(
            @Valid @RequestBody WorkbenchLayoutSaveReq req,
            HttpServletRequest request) {
        userCardService.saveCurrentLayout(req, RequestContextUtils.buildContext(request));
        return BaseResult.success();
    }

    @PostMapping("/layout/reset")
    @RequiresPermission(HomeWorkbenchConstants.PERMISSION_WORKBENCH_VIEW)
    @Operation(summary = "恢复默认工作台布局", description = "清理当前用户个性化布局并恢复系统默认布局")
    public BaseResult<Void> resetLayout(HttpServletRequest request) {
        userCardService.resetCurrentLayout(RequestContextUtils.buildContext(request));
        return BaseResult.success();
    }

    @PostMapping("/card/data")
    @RequiresPermission(HomeWorkbenchConstants.PERMISSION_WORKBENCH_VIEW)
    @Operation(summary = "查询卡片数据", description = "查询指定工作台卡片数据，并校验卡片启用状态和权限")
    public BaseResult<WorkbenchCardDataVO> cardData(
            @Valid @RequestBody WorkbenchCardDataReq req,
            HttpServletRequest request) {
        return BaseResult.success(userCardService.getCardData(req, RequestContextUtils.buildContext(request)));
    }

    @PostMapping("/quick-entry/list")
    @RequiresPermission({
            HomeWorkbenchConstants.PERMISSION_WORKBENCH_VIEW,
            HomeWorkbenchConstants.PERMISSION_CARD_QUICK_ENTRY
    })
    @Operation(summary = "查询我的快捷菜单", description = "查询当前用户可访问的系统默认菜单和个人快捷菜单")
    public BaseResult<WorkbenchQuickEntryListVO> listQuickEntries(
            @RequestBody(required = false) WorkbenchQuickEntryListReq req,
            HttpServletRequest request) {
        return BaseResult.success(quickEntryService.listCurrentUserEntries(req, RequestContextUtils.buildContext(request)));
    }

    @PostMapping("/quick-entry/save")
    @RequiresPermission({
            HomeWorkbenchConstants.PERMISSION_WORKBENCH_VIEW,
            HomeWorkbenchConstants.PERMISSION_CARD_QUICK_ENTRY
    })
    @Operation(summary = "保存我的快捷菜单", description = "新增或编辑当前用户个人快捷菜单")
    public BaseResult<WorkbenchQuickEntryVO> saveQuickEntry(
            @Valid @RequestBody WorkbenchQuickEntryReq req,
            HttpServletRequest request) {
        return BaseResult.success(quickEntryService.saveCurrentUserEntry(req, RequestContextUtils.buildContext(request)));
    }

    @PostMapping("/quick-entry/delete")
    @RequiresPermission({
            HomeWorkbenchConstants.PERMISSION_WORKBENCH_VIEW,
            HomeWorkbenchConstants.PERMISSION_CARD_QUICK_ENTRY
    })
    @Operation(summary = "删除我的快捷菜单", description = "删除当前用户个人快捷菜单")
    public BaseResult<Void> deleteQuickEntry(
            @Valid @RequestBody BaseReq req,
            HttpServletRequest request) {
        quickEntryService.deleteCurrentUserEntry(req.getId(), RequestContextUtils.buildContext(request));
        return BaseResult.success();
    }
}
