package com.lawoffice.message.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lawoffice.message.constant.MessageConstants;
import com.lawoffice.message.entity.SysMessage;
import com.lawoffice.message.entity.SysMessageReceiver;
import com.lawoffice.message.mapper.SysMessageMapper;
import com.lawoffice.message.mapper.SysMessageReceiverMapper;
import com.lawoffice.system.dto.BusinessDocumentAccessContext;
import com.lawoffice.system.service.IBusinessDocumentProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MessageBusinessDocumentProvider implements IBusinessDocumentProvider {

    private static final String MESSAGE_BIZ_TYPE = "message";

    private final SysMessageMapper messageMapper;
    private final SysMessageReceiverMapper receiverMapper;

    @Override
    public String bizType() {
        return MESSAGE_BIZ_TYPE;
    }

    @Override
    public String moduleName() {
        return "消息中心";
    }

    @Override
    public Map<String, String> resolveRecordNames(
            Collection<String> bizIds,
            BusinessDocumentAccessContext context) {
        if (bizIds == null || bizIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return messageMapper.selectList(Wrappers.lambdaQuery(SysMessage.class)
                        .select(SysMessage::getId, SysMessage::getTitle)
                        .in(SysMessage::getId, bizIds)
                        .eq(SysMessage::getTenantId, context.tenantId())
                        .eq(SysMessage::getDeleteFlag, 0))
                .stream()
                .collect(Collectors.toMap(
                        SysMessage::getId,
                        message -> StringUtils.hasText(message.getTitle())
                                ? message.getTitle()
                                : "未命名消息",
                        (left, right) -> left));
    }

    @Override
    public boolean canAccess(String bizId, BusinessDocumentAccessContext context) {
        if (!StringUtils.hasText(bizId)) {
            return false;
        }
        SysMessage message = getActiveTenantMessage(bizId, context);
        if (message == null) {
            return false;
        }
        if (context.userId().equals(message.getSenderId())) {
            return message.getSenderDeleteFlag() != null
                    && message.getSenderDeleteFlag() == MessageConstants.FLAG_NO;
        }
        return hasReceivedMessageAccess(bizId, context);
    }

    @Override
    public Set<String> filterAccessibleBizIds(
            Collection<String> bizIds,
            BusinessDocumentAccessContext context) {
        if (bizIds == null || bizIds.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> requestedIds = bizIds.stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (requestedIds.isEmpty()) {
            return Collections.emptySet();
        }

        List<SysMessage> messages = messageMapper.selectList(Wrappers.lambdaQuery(SysMessage.class)
                .select(SysMessage::getId, SysMessage::getSenderId, SysMessage::getSenderDeleteFlag)
                .in(SysMessage::getId, requestedIds)
                .eq(SysMessage::getTenantId, context.tenantId())
                .eq(SysMessage::getDeleteFlag, 0));

        Set<String> accessibleIds = new LinkedHashSet<>();
        Set<String> receivedCandidateIds = new LinkedHashSet<>();
        for (SysMessage message : messages) {
            if (context.userId().equals(message.getSenderId())) {
                if (message.getSenderDeleteFlag() != null
                        && message.getSenderDeleteFlag() == MessageConstants.FLAG_NO) {
                    accessibleIds.add(message.getId());
                }
                continue;
            }
            receivedCandidateIds.add(message.getId());
        }

        if (!receivedCandidateIds.isEmpty()) {
            receiverMapper.selectList(Wrappers.lambdaQuery(SysMessageReceiver.class)
                            .select(SysMessageReceiver::getMessageId)
                            .in(SysMessageReceiver::getMessageId, receivedCandidateIds)
                            .eq(SysMessageReceiver::getTenantId, context.tenantId())
                            .eq(SysMessageReceiver::getReceiverId, context.userId())
                            .eq(SysMessageReceiver::getDeleteFlag, 0))
                    .stream()
                    .map(SysMessageReceiver::getMessageId)
                    .filter(StringUtils::hasText)
                    .forEach(accessibleIds::add);
        }

        return accessibleIds;
    }

    private SysMessage getActiveTenantMessage(String messageId, BusinessDocumentAccessContext context) {
        return messageMapper.selectOne(Wrappers.lambdaQuery(SysMessage.class)
                .eq(SysMessage::getId, messageId)
                .eq(SysMessage::getTenantId, context.tenantId())
                .eq(SysMessage::getDeleteFlag, 0)
                .last("LIMIT 1"));
    }

    private boolean hasReceivedMessageAccess(String messageId, BusinessDocumentAccessContext context) {
        return receiverMapper.selectCount(Wrappers.lambdaQuery(SysMessageReceiver.class)
                .eq(SysMessageReceiver::getMessageId, messageId)
                .eq(SysMessageReceiver::getTenantId, context.tenantId())
                .eq(SysMessageReceiver::getReceiverId, context.userId())
                .eq(SysMessageReceiver::getDeleteFlag, 0)) > 0;
    }
}
