package com.lawoffice.message.controller;

import com.lawoffice.framework.annotation.AutoLog;
import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.framework.enums.LogType;
import com.lawoffice.framework.enums.OperateType;
import com.lawoffice.framework.req.BasePageReq;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.message.req.MessageIdReq;
import com.lawoffice.message.req.MessageIdsReq;
import com.lawoffice.message.req.MessageReceiverIdReq;
import com.lawoffice.message.req.MessageReceiverIdsReq;
import com.lawoffice.message.req.SendMessageReq;
import com.lawoffice.message.service.IMessageService;
import com.lawoffice.message.vo.MessageDetailVO;
import com.lawoffice.message.vo.MessageInboxVO;
import com.lawoffice.message.vo.MessageSendResultVO;
import com.lawoffice.message.vo.MessageSentVO;
import com.lawoffice.system.service.ISysFilesService;
import com.lawoffice.system.vo.FileUploadVO;
import com.lawoffice.util.HttpDownloadUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/message")
@RequiredArgsConstructor
@Tag(name = "站内消息", description = "站内消息收发、阅读和发送记录管理")
@ModuleInfo(value = "message", name = "站内消息", description = "站内消息收发、阅读和发送记录管理")
public class MessageController {

    private final IMessageService messageService;
    private final ISysFilesService sysFilesService;

    @PostMapping("/send")
    @Operation(summary = "发送站内消息", description = "给当前租户内指定用户发送站内消息")
    @AutoLog(value = "发送站内消息", logType = LogType.OPERATION, operateType = OperateType.SAVE)
    public BaseResult<MessageSendResultVO> send(
            @Valid @RequestBody SendMessageReq req,
            HttpServletRequest request) {
        return BaseResult.success(messageService.sendMessage(req, getUsername(request)));
    }

    @PostMapping("/inbox/page")
    @Operation(summary = "分页查询收件箱", description = "分页查询当前用户收到的站内消息")
    public BaseResult<PageVO<MessageInboxVO>> pageInbox(
            @RequestBody(required = false) BasePageReq req,
            HttpServletRequest request) {
        return BaseResult.success(messageService.pageInbox(req, getUsername(request)));
    }

    @PostMapping("/notifications/page")
    @Operation(summary = "查询当前用户未读通知", description = "查询当前登录用户收到的未读站内消息通知")
    public BaseResult<PageVO<MessageInboxVO>> pageNotifications(
            @RequestBody(required = false) BasePageReq req,
            HttpServletRequest request) {
        return BaseResult.success(messageService.pageCurrentNotifications(req, getUsername(request)));
    }

    @PostMapping("/notifications/read")
    @Operation(summary = "标记通知已读", description = "将当前登录用户的一条通知标记为已读")
    public BaseResult<Void> markNotificationRead(
            @Valid @RequestBody MessageReceiverIdReq req,
            HttpServletRequest request) {
        messageService.markRead(req.getId(), getUsername(request));
        return BaseResult.success();
    }

    @PostMapping("/notifications/clear")
    @Operation(summary = "清空未读通知", description = "将当前登录用户的未读通知全部标记为已读")
    public BaseResult<Void> clearNotifications(HttpServletRequest request) {
        messageService.markAllNotificationsRead(getUsername(request));
        return BaseResult.success();
    }

    @PostMapping("/sent/page")
    @Operation(summary = "分页查询发件箱", description = "分页查询当前用户发送的站内消息")
    public BaseResult<PageVO<MessageSentVO>> pageSent(
            @RequestBody(required = false) BasePageReq req,
            HttpServletRequest request) {
        return BaseResult.success(messageService.pageSent(req, getUsername(request)));
    }

    @PostMapping("/inbox/detail")
    @Operation(summary = "查询收件消息详情", description = "查询当前用户收到的站内消息详情")
    public BaseResult<MessageDetailVO> getInboxDetail(
            @Valid @RequestBody MessageReceiverIdReq req,
            HttpServletRequest request) {
        return BaseResult.success(messageService.getDetail(req.getId(), true, getUsername(request)));
    }

    @PostMapping("/sent/detail")
    @Operation(summary = "查询发件消息详情", description = "查询当前用户发送的站内消息详情")
    public BaseResult<MessageDetailVO> getSentDetail(
            @Valid @RequestBody MessageIdReq req,
            HttpServletRequest request) {
        return BaseResult.success(messageService.getDetail(req.getId(), false, getUsername(request)));
    }

    @PostMapping("/read")
    @Operation(summary = "标记已读", description = "将当前用户收件消息标记为已读")
    public BaseResult<Void> markRead(
            @Valid @RequestBody MessageReceiverIdReq req,
            HttpServletRequest request) {
        messageService.markRead(req.getId(), getUsername(request));
        return BaseResult.success();
    }

    @PostMapping("/read/batch")
    @Operation(summary = "批量标记已读", description = "将当前用户选中的收件消息批量标记为已读")
    public BaseResult<Void> markBatchRead(
            @Valid @RequestBody MessageReceiverIdsReq req,
            HttpServletRequest request) {
        messageService.markBatchRead(req.getIds(), getUsername(request));
        return BaseResult.success();
    }

    @PostMapping("/read/all")
    @Operation(summary = "全部标记已读", description = "将当前用户当前租户下的全部收件消息标记为已读")
    public BaseResult<Void> markAllRead(HttpServletRequest request) {
        messageService.markAllCurrentTenantRead(getUsername(request));
        return BaseResult.success();
    }

    @PostMapping("/star")
    @Operation(summary = "切换收藏", description = "切换当前用户收件消息的收藏状态")
    public BaseResult<Void> toggleStar(
            @Valid @RequestBody MessageReceiverIdReq req,
            HttpServletRequest request) {
        messageService.toggleStar(req.getId(), getUsername(request));
        return BaseResult.success();
    }

    @PostMapping("/inbox/delete")
    @Operation(summary = "删除收件消息", description = "逻辑删除当前用户收件箱消息")
    @AutoLog(value = "删除收件消息", logType = LogType.OPERATION, operateType = OperateType.DELETE)
    public BaseResult<Void> deleteInbox(
            @Valid @RequestBody MessageReceiverIdReq req,
            HttpServletRequest request) {
        messageService.deleteInbox(req.getId(), getUsername(request));
        return BaseResult.success();
    }

    @PostMapping("/inbox/delete/batch")
    @Operation(summary = "批量删除收件消息", description = "逻辑删除当前用户选中的收件箱消息")
    @AutoLog(value = "批量删除收件消息", logType = LogType.OPERATION, operateType = OperateType.DELETE)
    public BaseResult<Void> batchDeleteInbox(
            @Valid @RequestBody MessageReceiverIdsReq req,
            HttpServletRequest request) {
        messageService.batchDeleteInbox(req.getIds(), getUsername(request));
        return BaseResult.success();
    }

    @PostMapping("/recall")
    @Operation(summary = "撤回消息", description = "撤回当前用户已发送的站内消息")
    @AutoLog(value = "撤回站内消息", logType = LogType.OPERATION, operateType = OperateType.CUSTOM)
    public BaseResult<Void> recall(
            @Valid @RequestBody MessageIdReq req,
            HttpServletRequest request) {
        messageService.recall(req.getId(), getUsername(request));
        return BaseResult.success();
    }

    @PostMapping("/sent/delete")
    @Operation(summary = "删除发件消息", description = "逻辑删除当前用户发件箱消息")
    @AutoLog(value = "删除发件消息", logType = LogType.OPERATION, operateType = OperateType.DELETE)
    public BaseResult<Void> deleteSent(
            @Valid @RequestBody MessageIdReq req,
            HttpServletRequest request) {
        messageService.deleteSent(req.getId(), getUsername(request));
        return BaseResult.success();
    }

    @PostMapping("/sent/delete/batch")
    @Operation(summary = "批量删除发件消息", description = "逻辑删除当前用户选中的发件箱消息")
    @AutoLog(value = "批量删除发件消息", logType = LogType.OPERATION, operateType = OperateType.DELETE)
    public BaseResult<Void> batchDeleteSent(
            @Valid @RequestBody MessageIdsReq req,
            HttpServletRequest request) {
        messageService.batchDeleteSent(req.getIds(), getUsername(request));
        return BaseResult.success();
    }

    @GetMapping("/attachment/download/{fileId}")
    @Operation(summary = "下载消息附件", description = "按消息发件人或收件人权限下载附件")
    public void downloadAttachment(
            @PathVariable String fileId,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        messageService.checkAttachmentDownloadAccess(fileId, getUsername(request));
        FileUploadVO file = sysFilesService.getFileById(fileId);
        String fileName = HttpDownloadUtils.resolveDownloadFileName(file.getFileName());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(resolveContentType(file.getFileType()));
        response.setHeader("Content-Disposition", HttpDownloadUtils.buildContentDisposition(fileName));
        response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");

        try (InputStream inputStream = sysFilesService.downloadFileContent(fileId)) {
            inputStream.transferTo(response.getOutputStream());
        }
    }

    private String getUsername(HttpServletRequest request) {
        return (String) request.getAttribute("username");
    }

    private String resolveContentType(String fileType) {
        if (fileType != null && fileType.contains("/")) {
            return fileType;
        }
        return "application/octet-stream";
    }
}
