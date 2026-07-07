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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class MessageCardDataProvider extends AbstractWorkbenchCardDataProvider implements IWorkbenchCardDataProvider {

    private static final String TIMEOUT_BIZ_TYPE = "workflow_timeout";
    private static final String URGE_BIZ_TYPE = "workflow_urge";

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
        long unreadTotal = countMessages(MessageConstants.READ_STATUS_UNREAD, null, context);
        long readTotal = countMessages(MessageConstants.READ_STATUS_READ, null, context);
        long urgeTotal = countMessages(MessageConstants.READ_STATUS_UNREAD, URGE_BIZ_TYPE, context);
        long timeoutTotal = countMessages(MessageConstants.READ_STATUS_UNREAD, TIMEOUT_BIZ_TYPE, context);
        PageVO<MessageInboxVO> unreadPage = unreadTotal > 0
                ? loadMessagePage(fetchLimit, MessageConstants.READ_STATUS_UNREAD, context)
                : null;
        PageVO<MessageInboxVO> readPage = readTotal > 0
                ? loadMessagePage(fetchLimit, MessageConstants.READ_STATUS_READ, context)
                : null;
        PageVO<MessageInboxVO> urgePage = urgeTotal > 0
                ? loadMessagePage(fetchLimit, MessageConstants.READ_STATUS_UNREAD, URGE_BIZ_TYPE, context)
                : null;
        PageVO<MessageInboxVO> timeoutPage = timeoutTotal > 0
                ? loadMessagePage(fetchLimit, MessageConstants.READ_STATUS_UNREAD, TIMEOUT_BIZ_TYPE, context)
                : null;

        WorkbenchCardDataVO vo = emptyData(card);
        vo.getSummary().put("unreadTotal", unreadTotal);
        vo.getSummary().put("readTotal", readTotal);
        vo.getSummary().put("urgeTotal", urgeTotal);
        vo.getSummary().put("timeoutTotal", timeoutTotal);
        List<Map<String, Object>> items = new ArrayList<>();
        items.addAll(mapMessageItems(unreadPage, "unread-message"));
        items.addAll(mapMessageItems(urgePage, "urge-message"));
        items.addAll(mapMessageItems(timeoutPage, "timeout-message"));
        items.addAll(mapMessageItems(readPage, "read-message"));
        vo.setItems(items);
        return vo;
    }

    private long countMessages(int readStatus, String bizType, RequestContext context) {
        return messageService.countInbox(context.getUsername(), readStatus, bizType);
    }

    private PageVO<MessageInboxVO> loadMessagePage(int limit, int readStatus, RequestContext context) {
        return loadMessagePage(limit, readStatus, null, context);
    }

    private PageVO<MessageInboxVO> loadMessagePage(int limit, int readStatus, String bizType, RequestContext context) {
        BasePageReq pageReq = new BasePageReq();
        pageReq.setPageNum(1);
        pageReq.setPageSize(limit);
        Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("readStatus", readStatus);
        if (bizType != null) {
            queryParams.put("bizType", bizType);
        }
        pageReq.setQueryParams(queryParams);
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
}
