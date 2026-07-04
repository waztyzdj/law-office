package com.lawoffice.home.controller;

import com.lawoffice.framework.req.BaseReq;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.util.RequestContextUtils;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.home.constant.HomeWorkbenchConstants;
import com.lawoffice.home.req.WorkbenchCardPageReq;
import com.lawoffice.home.req.WorkbenchCardReq;
import com.lawoffice.home.req.WorkbenchCardSortReq;
import com.lawoffice.home.req.WorkbenchCardStatusReq;
import com.lawoffice.home.req.WorkbenchQuickEntryPageReq;
import com.lawoffice.home.req.WorkbenchQuickEntryReq;
import com.lawoffice.home.service.IWorkbenchCardService;
import com.lawoffice.home.service.IWorkbenchQuickEntryService;
import com.lawoffice.home.vo.WorkbenchCardVO;
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
@RequestMapping("/home/admin/workbench")
@RequiredArgsConstructor
@Tag(name = "工作台管理", description = "工作台卡片和系统快捷菜单管理")
public class WorkbenchAdminController {

    private final IWorkbenchCardService cardService;
    private final IWorkbenchQuickEntryService quickEntryService;

    @PostMapping("/card/page")
    @RequiresPermission(HomeWorkbenchConstants.PERMISSION_CARD_MANAGE)
    @Operation(summary = "分页查询工作台卡片", description = "分页查询当前租户工作台卡片配置")
    public BaseResult<PageVO<WorkbenchCardVO>> pageCards(
            @RequestBody(required = false) WorkbenchCardPageReq req,
            HttpServletRequest request) {
        return BaseResult.success(cardService.pageCards(req, RequestContextUtils.buildContext(request)));
    }

    @PostMapping("/card/detail")
    @RequiresPermission(HomeWorkbenchConstants.PERMISSION_CARD_MANAGE)
    @Operation(summary = "查询工作台卡片详情", description = "查询当前租户工作台卡片配置详情")
    public BaseResult<WorkbenchCardVO> cardDetail(
            @Valid @RequestBody BaseReq req,
            HttpServletRequest request) {
        return BaseResult.success(cardService.getCardDetail(req.getId(), RequestContextUtils.buildContext(request)));
    }

    @PostMapping("/card/save")
    @RequiresPermission(HomeWorkbenchConstants.PERMISSION_CARD_MANAGE)
    @Operation(summary = "保存工作台卡片", description = "新增或编辑工作台卡片配置")
    public BaseResult<WorkbenchCardVO> saveCard(
            @Valid @RequestBody WorkbenchCardReq req,
            HttpServletRequest request) {
        return BaseResult.success(cardService.saveCard(req, RequestContextUtils.buildContext(request)));
    }

    @PostMapping("/card/status")
    @RequiresPermission(HomeWorkbenchConstants.PERMISSION_CARD_MANAGE)
    @Operation(summary = "启用或停用工作台卡片", description = "启用或停用当前租户工作台卡片")
    public BaseResult<Void> updateCardStatus(
            @Valid @RequestBody WorkbenchCardStatusReq req,
            HttpServletRequest request) {
        cardService.updateStatus(req.getId(), req.getStatus(), RequestContextUtils.buildContext(request));
        return BaseResult.success();
    }

    @PostMapping("/card/sort")
    @RequiresPermission(HomeWorkbenchConstants.PERMISSION_CARD_MANAGE)
    @Operation(summary = "调整工作台卡片排序", description = "批量调整工作台卡片默认排序")
    public BaseResult<Void> updateCardSort(
            @Valid @RequestBody WorkbenchCardSortReq req,
            HttpServletRequest request) {
        cardService.updateSort(req, RequestContextUtils.buildContext(request));
        return BaseResult.success();
    }

    @PostMapping("/quick-entry/page")
    @RequiresPermission(HomeWorkbenchConstants.PERMISSION_CARD_MANAGE)
    @Operation(summary = "分页查询系统快捷菜单", description = "分页查询当前租户系统默认快捷菜单")
    public BaseResult<PageVO<WorkbenchQuickEntryVO>> pageSystemQuickEntries(
            @RequestBody(required = false) WorkbenchQuickEntryPageReq req,
            HttpServletRequest request) {
        return BaseResult.success(quickEntryService.pageSystemEntries(req, RequestContextUtils.buildContext(request)));
    }

    @PostMapping("/quick-entry/save")
    @RequiresPermission(HomeWorkbenchConstants.PERMISSION_CARD_MANAGE)
    @Operation(summary = "保存系统快捷菜单", description = "新增或编辑当前租户系统默认快捷菜单")
    public BaseResult<WorkbenchQuickEntryVO> saveSystemQuickEntry(
            @Valid @RequestBody WorkbenchQuickEntryReq req,
            HttpServletRequest request) {
        return BaseResult.success(quickEntryService.saveSystemEntry(req, RequestContextUtils.buildContext(request)));
    }

    @PostMapping("/quick-entry/status")
    @RequiresPermission(HomeWorkbenchConstants.PERMISSION_CARD_MANAGE)
    @Operation(summary = "启用或停用系统快捷菜单", description = "启用或停用当前租户系统默认快捷菜单")
    public BaseResult<Void> updateSystemQuickEntryStatus(
            @Valid @RequestBody WorkbenchCardStatusReq req,
            HttpServletRequest request) {
        quickEntryService.updateSystemStatus(req.getId(), req.getStatus(), RequestContextUtils.buildContext(request));
        return BaseResult.success();
    }
}
