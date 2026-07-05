package com.lawoffice.message.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawoffice.framework.config.TenantContextHolder;
import com.lawoffice.framework.req.BasePageReq;
import com.lawoffice.framework.util.QueryWrapperBuilderUtils;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.message.constant.MessageConstants;
import com.lawoffice.message.entity.SysMessage;
import com.lawoffice.message.entity.SysMessageAction;
import com.lawoffice.message.entity.SysMessageReceiver;
import com.lawoffice.message.entity.SysMessageSendRecord;
import com.lawoffice.message.mapper.SysMessageActionMapper;
import com.lawoffice.message.mapper.SysMessageMapper;
import com.lawoffice.message.mapper.SysMessageReceiverMapper;
import com.lawoffice.message.mapper.SysMessageSendRecordMapper;
import com.lawoffice.message.req.MessageActionReq;
import com.lawoffice.message.req.MessageAttachmentReq;
import com.lawoffice.message.req.SendMessageReq;
import com.lawoffice.message.service.IMessageService;
import com.lawoffice.message.vo.MessageActionVO;
import com.lawoffice.message.vo.MessageAttachmentVO;
import com.lawoffice.message.vo.MessageDetailVO;
import com.lawoffice.message.vo.MessageInboxVO;
import com.lawoffice.message.vo.MessageSendResultVO;
import com.lawoffice.message.vo.MessageSentVO;
import com.lawoffice.system.entity.SysFileRelation;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.entity.UserTenant;
import com.lawoffice.system.mapper.SysFileRelationMapper;
import com.lawoffice.system.mapper.UserMapper;
import com.lawoffice.system.mapper.UserTenantMapper;
import com.lawoffice.system.service.ISysFilesService;
import com.lawoffice.util.EntityFillUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements IMessageService {

    private static final int MAX_RECEIVER_COUNT = 200;
    private static final String CHANNEL_IN_APP = "1";
    private static final Set<String> INBOX_RECEIVER_QUERY_FIELDS = Set.of(
            "readStatus", "starFlag", "archiveFlag", "createTime"
    );
    private static final Set<String> INBOX_MESSAGE_QUERY_FIELDS = Set.of(
            "title", "senderName", "messageType", "priority", "sendTime"
    );
    private static final Set<String> INBOX_ACTION_QUERY_FIELDS = Set.of(
            "bizType"
    );
    private static final Set<String> INBOX_SORT_FIELDS = Set.of(
            "readStatus", "starFlag", "archiveFlag", "createTime", "sendTime"
    );
    private static final Set<String> SENT_QUERY_FIELDS = Set.of(
            "title", "messageType", "priority", "sendStatus", "sendTime"
    );

    private final SysMessageMapper messageMapper;
    private final SysMessageReceiverMapper receiverMapper;
    private final SysMessageActionMapper actionMapper;
    private final SysMessageSendRecordMapper sendRecordMapper;
    private final SysFileRelationMapper fileRelationMapper;
    private final UserMapper userMapper;
    private final UserTenantMapper userTenantMapper;
    private final ISysFilesService sysFilesService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageSendResultVO sendMessage(SendMessageReq req, String operatorUsername) {
        User sender = getActiveUserByUsername(operatorUsername);
        String tenantId = requireTenantId();
        SendMessageReq normalizedReq = normalizeSendMessageReq(req);
        List<User> receivers = getActiveTenantUsers(normalizedReq.getReceiverIds(), tenantId);

        SysMessage message = buildMessage(normalizedReq, sender, tenantId);
        messageMapper.insert(message);

        SysMessageSendRecord sendRecord = buildSendRecord(normalizedReq, message, sender, receivers, tenantId);
        sendRecordMapper.insert(sendRecord);

        SysMessage updateMessage = new SysMessage();
        updateMessage.setId(message.getId());
        updateMessage.setSendRecordId(sendRecord.getId());
        updateMessage.setUpdateBy(operatorUsername);
        updateMessage.setUpdateTime(LocalDateTime.now());
        messageMapper.updateById(updateMessage);

        saveReceivers(message, receivers, tenantId, operatorUsername);
        saveActions(message, normalizedReq.getActions(), tenantId, operatorUsername);
        saveAttachments(message, normalizedReq.getAttachments(), tenantId, operatorUsername);

        MessageSendResultVO result = new MessageSendResultVO();
        result.setMessageId(message.getId());
        result.setSendRecordId(sendRecord.getId());
        result.setSendBatchNo(sendRecord.getSendBatchNo());
        result.setReceiverCount(receivers.size());
        return result;
    }

    @Override
    public PageVO<MessageInboxVO> pageInbox(BasePageReq req, String username) {
        User currentUser = getActiveUserByUsername(username);
        String tenantId = requireTenantId();
        BasePageReq safeReq = req == null ? new BasePageReq() : req;
        validateInboxQuery(safeReq);
        List<String> messageIds = findInboxMessageIds(safeReq, tenantId);
        if (messageIds != null && messageIds.isEmpty()) {
            return new PageVO<>(new ArrayList<>(), 0, Math.max(safeReq.getPageNum(), 1), Math.max(safeReq.getPageSize(), 1));
        }

        QueryWrapper<SysMessageReceiver> wrapper = QueryWrapperBuilderUtils.build(buildInboxReceiverReq(safeReq));
        wrapper.eq("receiver_id", currentUser.getId())
                .eq("tenant_id", tenantId)
                .eq("delete_flag", 0);
        if (messageIds != null) {
            wrapper.in("message_id", messageIds);
        }
        if (!StringUtils.hasText(safeReq.getSortField())) {
            wrapper.orderByDesc("create_time");
        }

        Page<SysMessageReceiver> page = new Page<>(Math.max(safeReq.getPageNum(), 1), Math.max(safeReq.getPageSize(), 1));
        Page<SysMessageReceiver> resultPage = receiverMapper.selectPage(page, wrapper);
        List<MessageInboxVO> records = buildInboxRecords(resultPage.getRecords());
        return new PageVO<>(records, resultPage.getTotal(), resultPage.getCurrent(), resultPage.getSize());
    }

    @Override
    public PageVO<MessageInboxVO> pageCurrentNotifications(BasePageReq req, String username) {
        User currentUser = getActiveUserByUsername(username);
        String tenantId = requireTenantId();
        BasePageReq safeReq = req == null ? new BasePageReq() : req;
        LambdaQueryWrapper<SysMessageReceiver> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysMessageReceiver::getReceiverId, currentUser.getId())
                .eq(SysMessageReceiver::getTenantId, tenantId)
                .eq(SysMessageReceiver::getReadStatus, MessageConstants.READ_STATUS_UNREAD)
                .eq(SysMessageReceiver::getDeleteFlag, 0)
                .orderByDesc(SysMessageReceiver::getCreateTime);

        Page<SysMessageReceiver> page = new Page<>(Math.max(safeReq.getPageNum(), 1), Math.max(safeReq.getPageSize(), 1));
        Page<SysMessageReceiver> resultPage = receiverMapper.selectPage(page, wrapper);
        List<MessageInboxVO> records = buildInboxRecords(resultPage.getRecords());
        return new PageVO<>(records, resultPage.getTotal(), resultPage.getCurrent(), resultPage.getSize());
    }

    @Override
    public PageVO<MessageSentVO> pageSent(BasePageReq req, String username) {
        User sender = getActiveUserByUsername(username);
        String tenantId = requireTenantId();
        BasePageReq safeReq = req == null ? new BasePageReq() : req;
        validateSentQuery(safeReq);
        QueryWrapper<SysMessage> wrapper = QueryWrapperBuilderUtils.build(safeReq);
        wrapper.eq("sender_id", sender.getId())
                .eq("tenant_id", tenantId)
                .eq("sender_delete_flag", MessageConstants.FLAG_NO)
                .eq("delete_flag", 0);
        if (!StringUtils.hasText(safeReq.getSortField())) {
            wrapper.orderByDesc("send_time");
        }

        Page<SysMessage> page = new Page<>(Math.max(safeReq.getPageNum(), 1), Math.max(safeReq.getPageSize(), 1));
        Page<SysMessage> resultPage = messageMapper.selectPage(page, wrapper);
        List<MessageSentVO> records = buildSentRecords(resultPage.getRecords());
        return new PageVO<>(records, resultPage.getTotal(), resultPage.getCurrent(), resultPage.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MessageDetailVO getDetail(String id, boolean inbox, String username) {
        User currentUser = getActiveUserByUsername(username);
        if (!StringUtils.hasText(id)) {
            throw new IllegalArgumentException("消息ID不能为空");
        }

        SysMessageReceiver receiver = null;
        SysMessage message;
        if (inbox) {
            receiver = getReceiverForCurrentUser(id, currentUser.getId());
            message = getActiveMessage(receiver.getMessageId());
        } else {
            message = getSentMessageForCurrentUser(id, currentUser.getId());
        }

        MessageDetailVO detail = BeanUtil.copyProperties(message, MessageDetailVO.class);
        if (receiver != null) {
            markReceiverRead(receiver, username);
            detail.setReceiverMessageId(receiver.getId());
            detail.setReadStatus(MessageConstants.READ_STATUS_READ);
            detail.setStarFlag(receiver.getStarFlag());
            detail.setArchiveFlag(receiver.getArchiveFlag());
        }
        detail.setSenderAvatar(resolveUserAvatar(message.getSenderId()));
        List<SysMessageAction> actions = getActions(message.getId());
        detail.setReceiverNames(getReceiverNames(message.getId()));
        detail.setBizType(resolvePrimaryBizType(actions));
        detail.setActions(BeanUtil.copyToList(actions, MessageActionVO.class));
        detail.setAttachments(buildAttachmentVOs(message.getId()));
        return detail;
    }

    @Override
    public void checkAttachmentDownloadAccess(String fileId, String username) {
        User currentUser = getActiveUserByUsername(username);
        String tenantId = requireTenantId();
        if (!StringUtils.hasText(fileId)) {
            throw new IllegalArgumentException("附件文件ID不能为空");
        }

        LambdaQueryWrapper<SysFileRelation> relationWrapper = new LambdaQueryWrapper<>();
        relationWrapper.eq(SysFileRelation::getTenantId, tenantId)
                .eq(SysFileRelation::getFileId, fileId)
                .eq(SysFileRelation::getBizType, "message")
                .eq(SysFileRelation::getDeleteFlag, 0);
        List<String> messageIds = fileRelationMapper.selectList(relationWrapper).stream()
                .map(SysFileRelation::getBizId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
        if (messageIds.isEmpty()) {
            throw new IllegalArgumentException("附件不存在或已删除");
        }

        if (hasSentAttachmentAccess(messageIds, currentUser.getId(), tenantId)
                || hasReceivedAttachmentAccess(messageIds, currentUser.getId(), tenantId)) {
            return;
        }
        throw new IllegalArgumentException("无权下载该附件");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(String receiverMessageId, String username) {
        User currentUser = getActiveUserByUsername(username);
        SysMessageReceiver receiver = getReceiverForCurrentUser(receiverMessageId, currentUser.getId());
        markReceiverRead(receiver, username);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markBatchRead(List<String> receiverMessageIds, String username) {
        User currentUser = getActiveUserByUsername(username);
        String tenantId = requireTenantId();
        List<String> ids = normalizeIds(receiverMessageIds);
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("收件消息ID不能为空");
        }

        LambdaQueryWrapper<SysMessageReceiver> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysMessageReceiver::getId, ids)
                .eq(SysMessageReceiver::getReceiverId, currentUser.getId())
                .eq(SysMessageReceiver::getTenantId, tenantId)
                .eq(SysMessageReceiver::getDeleteFlag, 0);
        List<SysMessageReceiver> receivers = receiverMapper.selectList(wrapper);
        if (receivers.size() != ids.size()) {
            throw new IllegalArgumentException("消息不存在或无权访问");
        }
        markReceiversRead(receivers, username);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllCurrentTenantRead(String username) {
        User currentUser = getActiveUserByUsername(username);
        String tenantId = requireTenantId();
        LambdaQueryWrapper<SysMessageReceiver> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysMessageReceiver::getReceiverId, currentUser.getId())
                .eq(SysMessageReceiver::getTenantId, tenantId)
                .eq(SysMessageReceiver::getReadStatus, MessageConstants.READ_STATUS_UNREAD)
                .eq(SysMessageReceiver::getDeleteFlag, 0);
        markReceiversRead(receiverMapper.selectList(wrapper), username);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllNotificationsRead(String username) {
        User currentUser = getActiveUserByUsername(username);
        String tenantId = requireTenantId();
        LambdaQueryWrapper<SysMessageReceiver> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysMessageReceiver::getReceiverId, currentUser.getId())
                .eq(SysMessageReceiver::getTenantId, tenantId)
                .eq(SysMessageReceiver::getReadStatus, MessageConstants.READ_STATUS_UNREAD)
                .eq(SysMessageReceiver::getDeleteFlag, 0);
        markReceiversRead(receiverMapper.selectList(wrapper), username);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggleStar(String receiverMessageId, String username) {
        User currentUser = getActiveUserByUsername(username);
        SysMessageReceiver receiver = getReceiverForCurrentUser(receiverMessageId, currentUser.getId());
        SysMessageReceiver update = new SysMessageReceiver();
        update.setId(receiver.getId());
        update.setStarFlag(receiver.getStarFlag() != null && receiver.getStarFlag() == MessageConstants.FLAG_YES
                ? MessageConstants.FLAG_NO
                : MessageConstants.FLAG_YES);
        update.setUpdateBy(username);
        update.setUpdateTime(LocalDateTime.now());
        receiverMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteInbox(String receiverMessageId, String username) {
        User currentUser = getActiveUserByUsername(username);
        SysMessageReceiver receiver = getReceiverForCurrentUser(receiverMessageId, currentUser.getId());
        EntityFillUtils.fillDeleteFields(receiver, username);
        receiverMapper.updateById(receiver);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDeleteInbox(List<String> receiverMessageIds, String username) {
        User currentUser = getActiveUserByUsername(username);
        String tenantId = requireTenantId();
        List<String> ids = normalizeIds(receiverMessageIds);
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("收件消息ID不能为空");
        }

        LambdaQueryWrapper<SysMessageReceiver> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysMessageReceiver::getId, ids)
                .eq(SysMessageReceiver::getReceiverId, currentUser.getId())
                .eq(SysMessageReceiver::getTenantId, tenantId)
                .eq(SysMessageReceiver::getDeleteFlag, 0);
        List<SysMessageReceiver> receivers = receiverMapper.selectList(wrapper);
        if (receivers.size() != ids.size()) {
            throw new IllegalArgumentException("消息不存在或无权访问");
        }
        for (SysMessageReceiver receiver : receivers) {
            EntityFillUtils.fillDeleteFields(receiver, username);
            receiverMapper.updateById(receiver);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recall(String messageId, String username) {
        User sender = getActiveUserByUsername(username);
        SysMessage message = getSentMessageForCurrentUser(messageId, sender.getId());
        if (message.getSendStatus() != null && message.getSendStatus() == MessageConstants.SEND_STATUS_RECALLED) {
            return;
        }
        if (hasReadReceiver(message.getId())) {
            throw new IllegalArgumentException("消息已有接收人阅读，不能撤回");
        }
        SysMessage update = new SysMessage();
        update.setId(message.getId());
        update.setSendStatus(MessageConstants.SEND_STATUS_RECALLED);
        update.setUpdateBy(username);
        update.setUpdateTime(LocalDateTime.now());
        messageMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSent(String messageId, String username) {
        User sender = getActiveUserByUsername(username);
        SysMessage message = getSentMessageForCurrentUser(messageId, sender.getId());
        SysMessage update = new SysMessage();
        update.setId(message.getId());
        update.setSenderDeleteFlag(MessageConstants.FLAG_YES);
        update.setUpdateBy(username);
        update.setUpdateTime(LocalDateTime.now());
        messageMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchDeleteSent(List<String> messageIds, String username) {
        User sender = getActiveUserByUsername(username);
        String tenantId = requireTenantId();
        List<String> ids = normalizeIds(messageIds);
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("消息ID不能为空");
        }

        LambdaQueryWrapper<SysMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysMessage::getId, ids)
                .eq(SysMessage::getSenderId, sender.getId())
                .eq(SysMessage::getTenantId, tenantId)
                .eq(SysMessage::getSenderDeleteFlag, MessageConstants.FLAG_NO)
                .eq(SysMessage::getDeleteFlag, 0);
        List<SysMessage> messages = messageMapper.selectList(wrapper);
        if (messages.size() != ids.size()) {
            throw new IllegalArgumentException("消息不存在或无权访问");
        }
        LocalDateTime now = LocalDateTime.now();
        for (SysMessage message : messages) {
            SysMessage update = new SysMessage();
            update.setId(message.getId());
            update.setSenderDeleteFlag(MessageConstants.FLAG_YES);
            update.setUpdateBy(username);
            update.setUpdateTime(now);
            messageMapper.updateById(update);
        }
    }

    /**
     * 发送前统一清洗入参，避免空接收人、重复接收人和未校验动作进入后续事务。
     */
    private SendMessageReq normalizeSendMessageReq(SendMessageReq req) {
        if (req == null) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
        req.setTitle(trimToNull(req.getTitle()));
        req.setContent(trimToNull(req.getContent()));
        if (!StringUtils.hasText(req.getTitle())) {
            throw new IllegalArgumentException("消息标题不能为空");
        }
        if (req.getReceiverIds() == null || req.getReceiverIds().isEmpty()) {
            throw new IllegalArgumentException("接收人不能为空");
        }
        req.setReceiverIds(req.getReceiverIds().stream()
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList()));
        if (req.getReceiverIds().isEmpty()) {
            throw new IllegalArgumentException("接收人不能为空");
        }
        if (req.getReceiverIds().size() > MAX_RECEIVER_COUNT) {
            throw new IllegalArgumentException("单次发送接收人不能超过" + MAX_RECEIVER_COUNT + "人");
        }
        req.setContentType(defaultValue(req.getContentType(), MessageConstants.CONTENT_TYPE_TEXT));
        req.setMessageType(defaultValue(req.getMessageType(), MessageConstants.MESSAGE_TYPE_NORMAL));
        req.setPriority(defaultValue(req.getPriority(), MessageConstants.PRIORITY_NORMAL));
        req.setSendScene(defaultValue(req.getSendScene(), 1));
        req.setSendScope(defaultValue(req.getSendScope(), 1));
        req.setActions(req.getActions() == null ? new ArrayList<>() : req.getActions());
        req.setAttachments(req.getAttachments() == null ? new ArrayList<>() : req.getAttachments());
        validateActions(req.getActions());
        return req;
    }

    private SysMessage buildMessage(SendMessageReq req, User sender, String tenantId) {
        SysMessage message = new SysMessage();
        message.setId(UUID.randomUUID().toString().replace("-", ""));
        message.setTenantId(tenantId);
        message.setTitle(req.getTitle());
        message.setContent(req.getContent());
        message.setContentType(req.getContentType());
        message.setMessageType(req.getMessageType());
        message.setPriority(req.getPriority());
        message.setSenderId(sender.getId());
        message.setSenderName(getDisplayName(sender));
        message.setSendStatus(MessageConstants.SEND_STATUS_SENT);
        message.setSendTime(LocalDateTime.now());
        message.setExpireTime(req.getExpireTime());
        message.setSenderDeleteFlag(MessageConstants.FLAG_NO);
        message.setCreateBy(sender.getUsername());
        message.setCreateTime(LocalDateTime.now());
        message.setDeleteFlag(0);
        return message;
    }

    private SysMessageSendRecord buildSendRecord(SendMessageReq req, SysMessage message, User sender, List<User> receivers, String tenantId) {
        SysMessageSendRecord record = new SysMessageSendRecord();
        record.setId(UUID.randomUUID().toString().replace("-", ""));
        record.setTenantId(tenantId);
        record.setMessageId(message.getId());
        record.setSendBatchNo("MSG" + System.currentTimeMillis());
        record.setSendScene(req.getSendScene());
        record.setSendScope(req.getSendScope());
        record.setReceiverSnapshot(buildReceiverSnapshot(receivers));
        record.setChannelTypes(CHANNEL_IN_APP);
        record.setSenderId(sender.getId());
        record.setSenderName(getDisplayName(sender));
        record.setReceiverCount(receivers.size());
        record.setSuccessCount(receivers.size());
        record.setFailCount(0);
        record.setReadCount(0);
        record.setSendStatus(MessageConstants.SEND_RECORD_STATUS_SUCCESS);
        record.setSendTime(message.getSendTime());
        record.setFinishTime(LocalDateTime.now());
        record.setCreateBy(sender.getUsername());
        record.setCreateTime(LocalDateTime.now());
        record.setDeleteFlag(0);
        return record;
    }

    private void saveReceivers(SysMessage message, List<User> receivers, String tenantId, String operatorUsername) {
        for (User receiverUser : receivers) {
            SysMessageReceiver receiver = new SysMessageReceiver();
            receiver.setId(UUID.randomUUID().toString().replace("-", ""));
            receiver.setTenantId(tenantId);
            receiver.setMessageId(message.getId());
            receiver.setReceiverId(receiverUser.getId());
            receiver.setReceiverName(getDisplayName(receiverUser));
            receiver.setReadStatus(MessageConstants.READ_STATUS_UNREAD);
            receiver.setStarFlag(MessageConstants.FLAG_NO);
            receiver.setArchiveFlag(MessageConstants.FLAG_NO);
            receiver.setRemindStatus(MessageConstants.FLAG_YES);
            receiver.setLastRemindTime(LocalDateTime.now());
            receiver.setCreateBy(operatorUsername);
            receiver.setCreateTime(LocalDateTime.now());
            receiver.setDeleteFlag(0);
            receiverMapper.insert(receiver);
        }
    }

    private void saveActions(SysMessage message, List<MessageActionReq> actions, String tenantId, String operatorUsername) {
        int index = 0;
        for (MessageActionReq actionReq : actions) {
            SysMessageAction action = BeanUtil.copyProperties(actionReq, SysMessageAction.class);
            action.setId(UUID.randomUUID().toString().replace("-", ""));
            action.setTenantId(tenantId);
            action.setMessageId(message.getId());
            action.setActionType(defaultValue(action.getActionType(), MessageConstants.ACTION_TYPE_INTERNAL_ROUTE));
            action.setOpenType(defaultValue(action.getOpenType(), MessageConstants.OPEN_TYPE_CURRENT));
            action.setSortOrder(defaultValue(action.getSortOrder(), index++));
            action.setCreateBy(operatorUsername);
            action.setCreateTime(LocalDateTime.now());
            action.setDeleteFlag(0);
            actionMapper.insert(action);
        }
    }

    /**
     * 附件只保存统一文件中心的业务关系，不在消息表中复制文件地址，便于后续统一鉴权和逻辑删除。
     */
    private void saveAttachments(SysMessage message, List<MessageAttachmentReq> attachments, String tenantId, String operatorUsername) {
        int index = 0;
        for (MessageAttachmentReq attachmentReq : attachments) {
            if (attachmentReq == null || !StringUtils.hasText(attachmentReq.getFileId())) {
                continue;
            }
            com.lawoffice.system.req.FileRelationReq relationReq = new com.lawoffice.system.req.FileRelationReq();
            relationReq.setFileId(attachmentReq.getFileId());
            relationReq.setBizType("message");
            relationReq.setBizId(message.getId());
            relationReq.setRelationType(1);
            relationReq.setSortOrder(defaultValue(attachmentReq.getSortOrder(), index++));
            sysFilesService.bindFile(operatorUsername, relationReq);
        }
    }

    private List<MessageInboxVO> buildInboxRecords(List<SysMessageReceiver> receivers) {
        if (receivers.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, SysMessage> messageMap = getMessageMap(receivers.stream()
                .map(SysMessageReceiver::getMessageId)
                .collect(Collectors.toList()));
        Map<String, String> bizTypeMap = getPrimaryBizTypeMap(new ArrayList<>(messageMap.keySet()));
        Map<String, User> senderMap = getSenderMap(messageMap.values().stream()
                .map(SysMessage::getSenderId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList()));
        return receivers.stream()
                .map(receiver -> {
                    SysMessage message = messageMap.get(receiver.getMessageId());
                    MessageInboxVO vo = new MessageInboxVO();
                    vo.setId(receiver.getId());
                    vo.setMessageId(receiver.getMessageId());
                    vo.setReadStatus(receiver.getReadStatus());
                    vo.setStarFlag(receiver.getStarFlag());
                    vo.setArchiveFlag(receiver.getArchiveFlag());
                    vo.setReadTime(receiver.getReadTime());
                    if (message != null) {
                        vo.setTitle(message.getTitle());
                        vo.setMessageType(message.getMessageType());
                        vo.setBizType(bizTypeMap.get(message.getId()));
                        vo.setPriority(message.getPriority());
                        vo.setSenderId(message.getSenderId());
                        vo.setSenderName(message.getSenderName());
                        vo.setSenderAvatar(resolveUserAvatar(senderMap.get(message.getSenderId())));
                        vo.setSendStatus(message.getSendStatus());
                        vo.setSendTime(message.getSendTime());
                    }
                    return vo;
                })
                .collect(Collectors.toList());
    }

    private List<MessageSentVO> buildSentRecords(List<SysMessage> messages) {
        if (messages.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, List<SysMessageReceiver>> receiverMap = getReceivers(messages.stream()
                .map(SysMessage::getId)
                .collect(Collectors.toList()))
                .stream()
                .collect(Collectors.groupingBy(SysMessageReceiver::getMessageId));
        Map<String, String> bizTypeMap = getPrimaryBizTypeMap(messages.stream()
                .map(SysMessage::getId)
                .collect(Collectors.toList()));
        return messages.stream()
                .map(message -> {
                    List<SysMessageReceiver> receivers = receiverMap.getOrDefault(message.getId(), new ArrayList<>());
                    MessageSentVO vo = BeanUtil.copyProperties(message, MessageSentVO.class);
                    vo.setBizType(bizTypeMap.get(message.getId()));
                    vo.setReceiverCount(receivers.size());
                    vo.setReadCount((int) receivers.stream()
                            .filter(receiver -> receiver.getReadStatus() != null && receiver.getReadStatus() == MessageConstants.READ_STATUS_READ)
                            .count());
                    return vo;
                })
                .collect(Collectors.toList());
    }

    private User getActiveUserByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("未登录或登录已过期");
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username)
                .eq(User::getDeleteFlag, 0)
                .last("LIMIT 1");
        User user = userMapper.selectOne(wrapper);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new IllegalArgumentException("用户不存在或已被冻结");
        }
        return user;
    }

    /**
     * 发送对象必须属于当前租户且仍然有效，防止通过用户 ID 横向给其他租户用户发消息。
     */
    private List<User> getActiveTenantUsers(List<String> userIds, String tenantId) {
        LambdaQueryWrapper<UserTenant> tenantWrapper = new LambdaQueryWrapper<>();
        tenantWrapper.in(UserTenant::getUserId, userIds)
                .eq(UserTenant::getTenantId, tenantId)
                .eq(UserTenant::getStatus, "1")
                .eq(UserTenant::getDeleteFlag, 0);
        List<String> validUserIds = userTenantMapper.selectList(tenantWrapper).stream()
                .map(UserTenant::getUserId)
                .distinct()
                .collect(Collectors.toList());
        if (validUserIds.size() != userIds.size()) {
            throw new IllegalArgumentException("只能发送给当前租户内的有效用户");
        }

        LambdaQueryWrapper<User> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.in(User::getId, validUserIds)
                .eq(User::getStatus, 1)
                .eq(User::getDeleteFlag, 0);
        List<User> users = userMapper.selectList(userWrapper);
        Map<String, User> userMap = users.stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return userIds.stream().map(userMap::get).filter(user -> user != null).collect(Collectors.toList());
    }

    private SysMessageReceiver getReceiverForCurrentUser(String receiverMessageId, String userId) {
        String tenantId = requireTenantId();
        LambdaQueryWrapper<SysMessageReceiver> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysMessageReceiver::getId, receiverMessageId)
                .eq(SysMessageReceiver::getReceiverId, userId)
                .eq(SysMessageReceiver::getTenantId, tenantId)
                .eq(SysMessageReceiver::getDeleteFlag, 0)
                .last("LIMIT 1");
        SysMessageReceiver receiver = receiverMapper.selectOne(wrapper);
        if (receiver == null) {
            throw new IllegalArgumentException("消息不存在或无权访问");
        }
        return receiver;
    }

    private SysMessage getSentMessageForCurrentUser(String messageId, String senderId) {
        String tenantId = requireTenantId();
        LambdaQueryWrapper<SysMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysMessage::getId, messageId)
                .eq(SysMessage::getSenderId, senderId)
                .eq(SysMessage::getTenantId, tenantId)
                .eq(SysMessage::getSenderDeleteFlag, MessageConstants.FLAG_NO)
                .eq(SysMessage::getDeleteFlag, 0)
                .last("LIMIT 1");
        SysMessage message = messageMapper.selectOne(wrapper);
        if (message == null) {
            throw new IllegalArgumentException("消息不存在或无权访问");
        }
        return message;
    }

    private SysMessage getActiveMessage(String messageId) {
        String tenantId = requireTenantId();
        LambdaQueryWrapper<SysMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysMessage::getId, messageId)
                .eq(SysMessage::getTenantId, tenantId)
                .eq(SysMessage::getDeleteFlag, 0)
                .last("LIMIT 1");
        SysMessage message = messageMapper.selectOne(wrapper);
        if (message == null) {
            throw new IllegalArgumentException("消息不存在或已删除");
        }
        return message;
    }

    private Map<String, SysMessage> getMessageMap(List<String> messageIds) {
        if (messageIds.isEmpty()) {
            return new LinkedHashMap<>();
        }
        LambdaQueryWrapper<SysMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysMessage::getId, messageIds)
                .eq(SysMessage::getDeleteFlag, 0);
        return messageMapper.selectList(wrapper).stream().collect(Collectors.toMap(SysMessage::getId, Function.identity(), (left, right) -> left));
    }

    private Map<String, User> getSenderMap(List<String> senderIds) {
        if (senderIds.isEmpty()) {
            return new LinkedHashMap<>();
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(User::getId, senderIds)
                .eq(User::getDeleteFlag, 0);
        return userMapper.selectList(wrapper).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    private List<SysMessageReceiver> getReceivers(List<String> messageIds) {
        if (messageIds.isEmpty()) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<SysMessageReceiver> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysMessageReceiver::getMessageId, messageIds)
                .eq(SysMessageReceiver::getDeleteFlag, 0);
        return receiverMapper.selectList(wrapper);
    }

    /**
     * 消息列表需要展示动作业务类型，但不能逐条查询动作；取排序最靠前的有效动作作为该消息的主业务类型。
     */
    private Map<String, String> getPrimaryBizTypeMap(List<String> messageIds) {
        if (messageIds.isEmpty()) {
            return new LinkedHashMap<>();
        }
        LambdaQueryWrapper<SysMessageAction> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysMessageAction::getMessageId, messageIds)
                .eq(SysMessageAction::getDeleteFlag, 0)
                .orderByAsc(SysMessageAction::getMessageId)
                .orderByAsc(SysMessageAction::getSortOrder);
        Map<String, String> bizTypeMap = new LinkedHashMap<>();
        actionMapper.selectList(wrapper).forEach(action -> {
            if (StringUtils.hasText(action.getMessageId())
                    && StringUtils.hasText(action.getBizType())
                    && !bizTypeMap.containsKey(action.getMessageId())) {
                bizTypeMap.put(action.getMessageId(), action.getBizType());
            }
        });
        return bizTypeMap;
    }

    private List<SysMessageAction> getActions(String messageId) {
        LambdaQueryWrapper<SysMessageAction> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysMessageAction::getMessageId, messageId)
                .eq(SysMessageAction::getDeleteFlag, 0)
                .orderByAsc(SysMessageAction::getSortOrder);
        return actionMapper.selectList(wrapper);
    }

    private String resolvePrimaryBizType(List<SysMessageAction> actions) {
        return actions.stream()
                .map(SysMessageAction::getBizType)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private List<String> getReceiverNames(String messageId) {
        LambdaQueryWrapper<SysMessageReceiver> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysMessageReceiver::getMessageId, messageId)
                .eq(SysMessageReceiver::getDeleteFlag, 0);
        return receiverMapper.selectList(wrapper).stream()
                .sorted(Comparator.comparing(SysMessageReceiver::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(SysMessageReceiver::getReceiverName)
                .collect(Collectors.toList());
    }

    /**
     * 发件人只有在当前租户且未删除发件记录时，才允许下载消息附件。
     */
    private boolean hasSentAttachmentAccess(List<String> messageIds, String userId, String tenantId) {
        LambdaQueryWrapper<SysMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysMessage::getId, messageIds)
                .eq(SysMessage::getTenantId, tenantId)
                .eq(SysMessage::getSenderId, userId)
                .eq(SysMessage::getSenderDeleteFlag, MessageConstants.FLAG_NO)
                .eq(SysMessage::getDeleteFlag, 0)
                .last("LIMIT 1");
        return messageMapper.selectOne(wrapper) != null;
    }

    /**
     * 收件人只有存在当前租户的未删除收件记录时，才允许下载消息附件。
     */
    private boolean hasReceivedAttachmentAccess(List<String> messageIds, String userId, String tenantId) {
        LambdaQueryWrapper<SysMessageReceiver> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysMessageReceiver::getMessageId, messageIds)
                .eq(SysMessageReceiver::getTenantId, tenantId)
                .eq(SysMessageReceiver::getReceiverId, userId)
                .eq(SysMessageReceiver::getDeleteFlag, 0)
                .last("LIMIT 1");
        return receiverMapper.selectOne(wrapper) != null;
    }

    private boolean hasReadReceiver(String messageId) {
        LambdaQueryWrapper<SysMessageReceiver> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysMessageReceiver::getMessageId, messageId)
                .eq(SysMessageReceiver::getReadStatus, MessageConstants.READ_STATUS_READ)
                .eq(SysMessageReceiver::getDeleteFlag, 0)
                .last("LIMIT 1");
        return receiverMapper.selectOne(wrapper) != null;
    }

    /**
     * 已读操作保持幂等，避免重复打开详情时反复刷新发送记录的已读统计。
     */
    private void markReceiverRead(SysMessageReceiver receiver, String operatorUsername) {
        if (receiver.getReadStatus() != null && receiver.getReadStatus() == MessageConstants.READ_STATUS_READ) {
            return;
        }
        markReceiversRead(List.of(receiver), operatorUsername);
    }

    /**
     * 批量已读只刷新涉及到的消息统计一次，避免逐条刷新发送记录造成多余数据库更新。
     */
    private void markReceiversRead(List<SysMessageReceiver> receivers, String operatorUsername) {
        List<SysMessageReceiver> unreadReceivers = receivers.stream()
                .filter(receiver -> receiver.getReadStatus() == null
                        || receiver.getReadStatus() != MessageConstants.READ_STATUS_READ)
                .collect(Collectors.toList());
        if (unreadReceivers.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (SysMessageReceiver receiver : unreadReceivers) {
            SysMessageReceiver update = new SysMessageReceiver();
            update.setId(receiver.getId());
            update.setReadStatus(MessageConstants.READ_STATUS_READ);
            update.setReadTime(now);
            update.setUpdateBy(operatorUsername);
            update.setUpdateTime(now);
            receiverMapper.updateById(update);
        }
        unreadReceivers.stream()
                .map(SysMessageReceiver::getMessageId)
                .filter(StringUtils::hasText)
                .distinct()
                .forEach(messageId -> refreshReadCount(messageId, operatorUsername));
    }

    private List<String> normalizeIds(List<String> ids) {
        if (ids == null) {
            return new ArrayList<>();
        }
        return ids.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 发送记录中的已读数由收件明细汇总得出，撤回校验和发件箱统计都以这里的结果为准。
     */
    private void refreshReadCount(String messageId, String operatorUsername) {
        LambdaQueryWrapper<SysMessageReceiver> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysMessageReceiver::getMessageId, messageId)
                .eq(SysMessageReceiver::getDeleteFlag, 0);
        List<SysMessageReceiver> receivers = receiverMapper.selectList(wrapper);
        int readCount = (int) receivers.stream()
                .filter(receiver -> receiver.getReadStatus() != null && receiver.getReadStatus() == MessageConstants.READ_STATUS_READ)
                .count();

        LambdaQueryWrapper<SysMessageSendRecord> recordWrapper = new LambdaQueryWrapper<>();
        recordWrapper.eq(SysMessageSendRecord::getMessageId, messageId)
                .eq(SysMessageSendRecord::getDeleteFlag, 0)
                .last("LIMIT 1");
        SysMessageSendRecord record = sendRecordMapper.selectOne(recordWrapper);
        if (record == null) {
            return;
        }
        SysMessageSendRecord update = new SysMessageSendRecord();
        update.setId(record.getId());
        update.setReadCount(readCount);
        update.setUpdateBy(operatorUsername);
        update.setUpdateTime(LocalDateTime.now());
        sendRecordMapper.updateById(update);
    }

    /**
     * 动作链接在入库前校验，避免把不受支持的外链协议或不可打开的内部动作下发给客户端。
     */
    private void validateActions(List<MessageActionReq> actions) {
        for (MessageActionReq action : actions) {
            int actionType = defaultValue(action.getActionType(), MessageConstants.ACTION_TYPE_INTERNAL_ROUTE);
            if (actionType == MessageConstants.ACTION_TYPE_EXTERNAL_URL) {
                String externalUrl = trimToNull(action.getExternalUrl());
                if (!StringUtils.hasText(externalUrl) || !(externalUrl.startsWith("http://") || externalUrl.startsWith("https://"))) {
                    throw new IllegalArgumentException("外部链接仅支持http或https地址");
                }
            }
            if ((actionType == MessageConstants.ACTION_TYPE_INTERNAL_ROUTE || actionType == MessageConstants.ACTION_TYPE_TODO)
                    && !StringUtils.hasText(action.getRoutePath())
                    && !StringUtils.hasText(action.getBizId())) {
                throw new IllegalArgumentException("内部动作需配置路由路径或业务ID");
            }
        }
    }

    private BasePageReq buildInboxReceiverReq(BasePageReq req) {
        BasePageReq receiverReq = new BasePageReq();
        receiverReq.setPageNum(req.getPageNum());
        receiverReq.setPageSize(req.getPageSize());
        receiverReq.setQueryParams(extractQueryParams(req, INBOX_RECEIVER_QUERY_FIELDS));
        if (StringUtils.hasText(req.getSortField())) {
            if ("sendTime".equals(req.getSortField())) {
                receiverReq.setSortField("createTime");
                receiverReq.setSortOrder(req.getSortOrder());
            } else if (INBOX_RECEIVER_QUERY_FIELDS.contains(req.getSortField())) {
                receiverReq.setSortField(req.getSortField());
                receiverReq.setSortOrder(req.getSortOrder());
            }
        }
        return receiverReq;
    }

    /**
     * 收件箱筛选可能涉及收件表、消息主表和动作表，先收窄消息 ID 后再回到收件表分页。
     */
    private List<String> findInboxMessageIds(BasePageReq req, String tenantId) {
        Map<String, Object> messageQueryParams = extractQueryParams(req, INBOX_MESSAGE_QUERY_FIELDS);
        Map<String, Object> actionQueryParams = extractQueryParams(req, INBOX_ACTION_QUERY_FIELDS);
        if (messageQueryParams.isEmpty() && actionQueryParams.isEmpty()) {
            return null;
        }

        List<String> messageIds = null;
        if (!messageQueryParams.isEmpty()) {
            messageIds = findMessageIdsByMessageQuery(messageQueryParams, tenantId);
        }
        if (actionQueryParams.isEmpty()) {
            return messageIds;
        }
        List<String> actionMessageIds = findMessageIdsByActionQuery(actionQueryParams, tenantId);
        if (messageIds == null) {
            return actionMessageIds;
        }
        Set<String> actionMessageIdSet = actionMessageIds.stream().collect(Collectors.toSet());
        return messageIds.stream()
                .filter(actionMessageIdSet::contains)
                .collect(Collectors.toList());
    }

    private List<String> findMessageIdsByMessageQuery(Map<String, Object> messageQueryParams, String tenantId) {
        BasePageReq messageReq = new BasePageReq();
        messageReq.setQueryParams(messageQueryParams);
        QueryWrapper<SysMessage> wrapper = QueryWrapperBuilderUtils.build(messageReq);
        wrapper.eq("tenant_id", tenantId)
                .eq("delete_flag", 0);
        return messageMapper.selectList(wrapper).stream()
                .map(SysMessage::getId)
                .collect(Collectors.toList());
    }

    private List<String> findMessageIdsByActionQuery(Map<String, Object> actionQueryParams, String tenantId) {
        BasePageReq actionReq = new BasePageReq();
        actionReq.setQueryParams(actionQueryParams);
        QueryWrapper<SysMessageAction> wrapper = QueryWrapperBuilderUtils.build(actionReq);
        wrapper.eq("tenant_id", tenantId)
                .eq("delete_flag", 0);
        return actionMapper.selectList(wrapper).stream()
                .map(SysMessageAction::getMessageId)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    private Map<String, Object> extractQueryParams(BasePageReq req, Set<String> fields) {
        Map<String, Object> result = new HashMap<>();
        if (req == null || req.getQueryParams() == null) {
            return result;
        }
        for (Map.Entry<String, Object> entry : req.getQueryParams().entrySet()) {
            Object value = entry.getValue();
            if (value == null || (value instanceof String text && !StringUtils.hasText(text))) {
                continue;
            }
            if (fields.contains(getQueryFieldName(entry.getKey()))) {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    private void validateInboxQuery(BasePageReq req) {
        validateQueryFields(req, INBOX_SORT_FIELDS, mergeFields(
                mergeFields(INBOX_RECEIVER_QUERY_FIELDS, INBOX_MESSAGE_QUERY_FIELDS),
                INBOX_ACTION_QUERY_FIELDS));
    }

    private void validateSentQuery(BasePageReq req) {
        validateQueryFields(req, SENT_QUERY_FIELDS, SENT_QUERY_FIELDS);
    }

    /**
     * 所有筛选和排序字段先走白名单，避免前端参数直接进入动态查询构造。
     */
    private void validateQueryFields(BasePageReq req, Set<String> allowedSortFields, Set<String> allowedQueryFields) {
        if (req == null) {
            return;
        }
        if (StringUtils.hasText(req.getSortField())) {
            for (String sortField : req.getSortField().split(",")) {
                if (!allowedSortFields.contains(sortField.trim())) {
                    throw new IllegalArgumentException("不支持的排序字段");
                }
            }
        }
        if (req.getQueryParams() == null) {
            return;
        }
        for (String queryKey : req.getQueryParams().keySet()) {
            if (!allowedQueryFields.contains(getQueryFieldName(queryKey))) {
                throw new IllegalArgumentException("不支持的筛选字段");
            }
        }
    }

    private Set<String> mergeFields(Set<String> first, Set<String> second) {
        return java.util.stream.Stream.concat(first.stream(), second.stream())
                .collect(Collectors.toSet());
    }

    private String getQueryFieldName(String queryKey) {
        int index = queryKey.lastIndexOf('_');
        if (index > 0) {
            return queryKey.substring(0, index);
        }
        return queryKey;
    }

    private String buildReceiverSnapshot(List<User> receivers) {
        List<Map<String, String>> snapshot = receivers.stream()
                .map(user -> Map.of("id", user.getId(), "name", getDisplayName(user)))
                .collect(Collectors.toList());
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private String getDisplayName(User user) {
        if (user == null) {
            return "";
        }
        return StringUtils.hasText(user.getRealname()) ? user.getRealname() : user.getUsername();
    }

    private String resolveUserAvatar(String userId) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getId, userId)
                .eq(User::getDeleteFlag, 0)
                .last("LIMIT 1");
        User user = userMapper.selectOne(wrapper);
        return resolveUserAvatar(user);
    }

    /**
     * 头像优先解析文件中心地址，解析失败时保留原值，兼容历史上直接存 URL 的用户数据。
     */
    private String resolveUserAvatar(User user) {
        if (user == null || !StringUtils.hasText(user.getAvatar())) {
            return null;
        }
        try {
            return sysFilesService.getFileById(user.getAvatar()).getFileUrl();
        } catch (Exception ignored) {
            return user.getAvatar();
        }
    }

    private String requireTenantId() {
        String tenantId = TenantContextHolder.getCurrentTenantId();
        if (!StringUtils.hasText(tenantId)) {
            throw new IllegalArgumentException("当前租户不能为空");
        }
        return tenantId;
    }

    private Integer defaultValue(Integer value, Integer defaultValue) {
        return value == null ? defaultValue : value;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private List<MessageAttachmentVO> buildAttachmentVOs(String messageId) {
        List<com.lawoffice.system.vo.FileUploadVO> files = sysFilesService.listFilesByBiz("message", messageId);
        if (files.isEmpty()) {
            return new ArrayList<>();
        }
        return files.stream()
                .map(file -> {
                    MessageAttachmentVO vo = new MessageAttachmentVO();
                    vo.setFileId(file.getFileId());
                    vo.setFileName(file.getFileName());
                    vo.setFileType(file.getFileType());
                    vo.setFileSize(file.getFileSize() == null ? null : file.getFileSize() / 1024.0);
                    return vo;
                })
                .collect(Collectors.toList());
    }
}
