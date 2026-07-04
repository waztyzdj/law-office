package com.lawoffice.home.service.impl.card;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.req.BasePageReq;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.home.constant.HomeWorkbenchConstants;
import com.lawoffice.home.entity.WorkbenchCard;
import com.lawoffice.home.req.WorkbenchCardDataReq;
import com.lawoffice.home.service.IWorkbenchCardDataProvider;
import com.lawoffice.home.vo.WorkbenchCardDataVO;
import com.lawoffice.message.constant.MessageConstants;
import com.lawoffice.message.service.IMessageService;
import com.lawoffice.message.vo.MessageInboxVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class MessageCardDataProvider extends AbstractWorkbenchCardDataProvider implements IWorkbenchCardDataProvider {

    private static final Set<String> TIMEOUT_BIZ_TYPES = Set.of("workflow_timeout");
    private static final Set<String> URGE_BIZ_TYPES = Set.of("workflow_urge");

    private final IMessageService messageService;

    public MessageCardDataProvider(IMessageService messageService) {
        this.messageService = messageService;
    }

    @Override
    public boolean supports(String cardCode) {
        return HomeWorkbenchConstants.CARD_MESSAGE.equals(cardCode);
    }

    @Override
    public WorkbenchCardDataVO loadData(WorkbenchCardDataReq req, WorkbenchCard card, RequestContext context) {
        int fetchLimit = resolveListFetchLimit();
        PageVO<MessageInboxVO> unreadPage = loadMessagePage(fetchLimit, MessageConstants.READ_STATUS_UNREAD, context);
        PageVO<MessageInboxVO> readPage = loadMessagePage(fetchLimit, MessageConstants.READ_STATUS_READ, context);

        WorkbenchCardDataVO vo = emptyData(card);
        vo.getSummary().put("unreadTotal", total(unreadPage));
        vo.getSummary().put("readTotal", total(readPage));
        vo.getSummary().put("urgeTotal", countSpecialMessages(unreadPage, URGE_BIZ_TYPES));
        vo.getSummary().put("timeoutTotal", countSpecialMessages(unreadPage, TIMEOUT_BIZ_TYPES));
        long urgent = unreadPage == null || unreadPage.getRecords() == null ? 0 : unreadPage.getRecords().stream()
                .filter(message -> message.getPriority() != null && message.getPriority() >= MessageConstants.PRIORITY_URGENT)
                .count();
        vo.getSummary().put("urgent", urgent);
        List<Map<String, Object>> items = new ArrayList<>();
        items.addAll(mapMessageItems(unreadPage, "unread-message"));
        items.addAll(mapSpecialMessageItems(unreadPage, "urge-message", URGE_BIZ_TYPES));
        items.addAll(mapSpecialMessageItems(unreadPage, "timeout-message", TIMEOUT_BIZ_TYPES));
        items.addAll(mapMessageItems(readPage, "read-message"));
        vo.setItems(items);
        return vo;
    }

    private PageVO<MessageInboxVO> loadMessagePage(int limit, int readStatus, RequestContext context) {
        BasePageReq pageReq = new BasePageReq();
        pageReq.setPageNum(1);
        pageReq.setPageSize(limit);
        pageReq.setQueryParams(Map.of("readStatus", readStatus));
        pageReq.setSortField("createTime");
        pageReq.setSortOrder("desc");
        return messageService.pageInbox(pageReq, context.getUsername());
    }

    private List<Map<String, Object>> mapMessageItems(PageVO<MessageInboxVO> page, String type) {
        if (page == null || page.getRecords() == null) {
            return List.of();
        }
        return page.getRecords().stream()
                .map(message -> item(
                        message.getId(),
                        message.getTitle(),
                        type,
                        message.getReadStatus() != null && message.getReadStatus() == MessageConstants.READ_STATUS_UNREAD ? "unread" : "read",
                        message.getSendTime(),
                        HomeWorkbenchConstants.TARGET_TYPE_ROUTE,
                        "/message/inbox",
                        message.getMessageId()))
                .toList();
    }

    private long countSpecialMessages(PageVO<MessageInboxVO> page, Set<String> bizTypes) {
        if (page == null || page.getRecords() == null) {
            return 0;
        }
        return page.getRecords().stream()
                .filter(message -> isUnreadMessageOfTypes(message, bizTypes))
                .count();
    }

    private List<Map<String, Object>> mapSpecialMessageItems(PageVO<MessageInboxVO> page, String type, Set<String> bizTypes) {
        if (page == null || page.getRecords() == null) {
            return List.of();
        }
        return page.getRecords().stream()
                .filter(message -> isUnreadMessageOfTypes(message, bizTypes))
                .map(message -> item(
                        message.getId(),
                        message.getTitle(),
                        type,
                        "unread",
                        message.getSendTime(),
                        HomeWorkbenchConstants.TARGET_TYPE_ROUTE,
                        "/message/inbox",
                        message.getMessageId()))
                .toList();
    }

    private boolean isUnreadMessageOfTypes(MessageInboxVO message, Set<String> bizTypes) {
        return message != null
                && message.getReadStatus() != null
                && message.getReadStatus() == MessageConstants.READ_STATUS_UNREAD
                && bizTypes.contains(message.getBizType());
    }
}
