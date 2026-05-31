package com.lawoffice.message.service;

import com.lawoffice.framework.req.BasePageReq;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.message.req.SendMessageReq;
import com.lawoffice.message.vo.MessageDetailVO;
import com.lawoffice.message.vo.MessageInboxVO;
import com.lawoffice.message.vo.MessageSendResultVO;
import com.lawoffice.message.vo.MessageSentVO;

import java.util.List;

/**
 * 站内消息业务服务。
 */
public interface IMessageService {

    /**
     * 发送站内消息给当前租户内的指定用户。
     *
     * @param req 发送请求
     * @param operatorUsername 当前操作人账号
     * @return 发送结果
     */
    MessageSendResultVO sendMessage(SendMessageReq req, String operatorUsername);

    /**
     * 分页查询当前用户收件箱。
     *
     * @param req 分页查询请求
     * @param username 当前用户账号
     * @return 收件箱分页
     */
    PageVO<MessageInboxVO> pageInbox(BasePageReq req, String username);

    /**
     * 查询当前用户未读消息通知。
     *
     * @param req 分页查询请求
     * @param username 当前用户账号
     * @return 未读消息通知分页
     */
    PageVO<MessageInboxVO> pageCurrentNotifications(BasePageReq req, String username);

    /**
     * 分页查询当前用户发件箱。
     *
     * @param req 分页查询请求
     * @param username 当前用户账号
     * @return 发件箱分页
     */
    PageVO<MessageSentVO> pageSent(BasePageReq req, String username);

    /**
     * 查询消息详情，按收件人或发件人权限限制访问。
     *
     * @param id 消息ID或收件消息ID
     * @param inbox 是否按收件箱详情查询
     * @param username 当前用户账号
     * @return 消息详情
     */
    MessageDetailVO getDetail(String id, boolean inbox, String username);

    /**
     * 校验当前用户是否可以下载指定消息附件。
     *
     * @param fileId 附件文件ID
     * @param username 当前用户账号
     */
    void checkAttachmentDownloadAccess(String fileId, String username);

    /**
     * 标记收件消息为已读。
     *
     * @param receiverMessageId 收件消息ID
     * @param username 当前用户账号
     */
    void markRead(String receiverMessageId, String username);

    /**
     * 批量标记当前用户选中的收件消息为已读。
     *
     * @param receiverMessageIds 收件消息ID列表
     * @param username 当前用户账号
     */
    void markBatchRead(List<String> receiverMessageIds, String username);

    /**
     * 将当前用户当前租户下的所有未读收件消息标记为已读。
     *
     * @param username 当前用户账号
     */
    void markAllCurrentTenantRead(String username);

    /**
     * 将当前用户所有未读消息通知标记为已读。
     *
     * @param username 当前用户账号
     */
    void markAllNotificationsRead(String username);

    /**
     * 切换收件消息收藏状态。
     *
     * @param receiverMessageId 收件消息ID
     * @param username 当前用户账号
     */
    void toggleStar(String receiverMessageId, String username);

    /**
     * 逻辑删除当前用户收件箱消息。
     *
     * @param receiverMessageId 收件消息ID
     * @param username 当前用户账号
     */
    void deleteInbox(String receiverMessageId, String username);

    /**
     * 批量逻辑删除当前用户选中的收件箱消息。
     *
     * @param receiverMessageIds 收件消息ID列表
     * @param username 当前用户账号
     */
    void batchDeleteInbox(List<String> receiverMessageIds, String username);

    /**
     * 撤回当前用户发送的消息。
     *
     * @param messageId 消息ID
     * @param username 当前用户账号
     */
    void recall(String messageId, String username);

    /**
     * 删除当前用户发件箱消息。
     *
     * @param messageId 消息ID
     * @param username 当前用户账号
     */
    void deleteSent(String messageId, String username);

    /**
     * 批量逻辑删除当前用户选中的发件箱消息。
     *
     * @param messageIds 消息ID列表
     * @param username 当前用户账号
     */
    void batchDeleteSent(List<String> messageIds, String username);
}
