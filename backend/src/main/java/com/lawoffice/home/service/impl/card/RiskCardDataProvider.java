package com.lawoffice.home.service.impl.card;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.req.BasePageReq;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.home.constant.HomeWorkbenchConstants;
import com.lawoffice.home.entity.WorkbenchCard;
import com.lawoffice.home.req.WorkbenchCardDataReq;
import com.lawoffice.home.service.IWorkbenchCardDataProvider;
import com.lawoffice.home.vo.WorkbenchCardDataVO;
import com.lawoffice.message.constant.MessageConstants;
import com.lawoffice.message.service.IMessageService;
import com.lawoffice.message.vo.MessageInboxVO;
import com.lawoffice.workflow.req.TaskPageReq;
import com.lawoffice.workflow.service.IRuntimeService;
import com.lawoffice.workflow.vo.RuntimeTaskVO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class RiskCardDataProvider extends AbstractWorkbenchCardDataProvider implements IWorkbenchCardDataProvider {

    private static final int SCAN_LIMIT = 50;

    private final IRuntimeService runtimeService;
    private final IMessageService messageService;

    public RiskCardDataProvider(IRuntimeService runtimeService, IMessageService messageService) {
        this.runtimeService = runtimeService;
        this.messageService = messageService;
    }

    @Override
    public boolean supports(String cardCode) {
        return HomeWorkbenchConstants.CARD_RISK.equals(cardCode);
    }

    @Override
    public WorkbenchCardDataVO loadData(WorkbenchCardDataReq req, WorkbenchCard card, RequestContext context) {
        List<Map<String, Object>> risks = new ArrayList<>();
        risks.addAll(loadOverdueWorkflowRisks(context));
        risks.addAll(loadUrgentMessageRisks(context));
        risks.sort(Comparator.comparing(item -> String.valueOf(item.get("occurTime")), Comparator.reverseOrder()));

        int limit = resolveLimit(req, card);
        WorkbenchCardDataVO vo = emptyData(card);
        vo.getSummary().put("total", risks.size());
        vo.getSummary().put("high", risks.stream().filter(item -> "high".equals(item.get("level"))).count());
        vo.getSummary().put("medium", risks.stream().filter(item -> "medium".equals(item.get("level"))).count());
        vo.setItems(risks.stream().limit(limit).toList());
        return vo;
    }

    private List<Map<String, Object>> loadOverdueWorkflowRisks(RequestContext context) {
        TaskPageReq req = new TaskPageReq();
        req.setPageNum(1);
        req.setPageSize(SCAN_LIMIT);
        BaseResult<PageVO<RuntimeTaskVO>> result = runtimeService.pageTodo(req, context);
        if (result.getCode() == null || result.getCode() != 200 || result.getData() == null
                || result.getData().getRecords() == null) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        return result.getData().getRecords().stream()
                .filter(task -> task.getDueTime() != null && task.getDueTime().isBefore(now))
                .map(task -> {
                    Map<String, Object> item = item(
                            task.getId(),
                            task.getInstanceTitle(),
                            HomeWorkbenchConstants.RECORD_TYPE_WORKFLOW,
                            "overdue",
                            task.getDueTime(),
                            HomeWorkbenchConstants.TARGET_TYPE_ROUTE,
                            "/workflow/todo",
                            task.getId());
                    item.put("level", "high");
                    item.put("source", "审批超时");
                    return item;
                })
                .toList();
    }

    private List<Map<String, Object>> loadUrgentMessageRisks(RequestContext context) {
        BasePageReq req = new BasePageReq();
        req.setPageNum(1);
        req.setPageSize(SCAN_LIMIT);
        PageVO<MessageInboxVO> page = messageService.pageCurrentNotifications(req, context.getUsername());
        if (page == null || page.getRecords() == null) {
            return List.of();
        }
        return page.getRecords().stream()
                .filter(message -> message.getPriority() != null && message.getPriority() >= MessageConstants.PRIORITY_URGENT)
                .map(message -> {
                    Map<String, Object> item = item(
                            message.getId(),
                            message.getTitle(),
                            HomeWorkbenchConstants.RECORD_TYPE_MESSAGE,
                            "urgent",
                            message.getSendTime(),
                            HomeWorkbenchConstants.TARGET_TYPE_ROUTE,
                            "/message/inbox",
                            message.getMessageId());
                    item.put("level", "medium");
                    item.put("source", "高优先级消息");
                    return item;
                })
                .toList();
    }
}
