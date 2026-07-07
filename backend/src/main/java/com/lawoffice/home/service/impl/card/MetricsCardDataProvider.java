package com.lawoffice.home.service.impl.card;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.home.constant.HomeWorkbenchConstants;
import com.lawoffice.home.entity.WorkbenchCard;
import com.lawoffice.home.req.WorkbenchCardDataReq;
import com.lawoffice.home.service.IWorkbenchCardDataProvider;
import com.lawoffice.home.vo.WorkbenchCardDataVO;
import com.lawoffice.message.constant.MessageConstants;
import com.lawoffice.message.service.IMessageService;
import com.lawoffice.workflow.service.IRuntimeService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class MetricsCardDataProvider extends AbstractWorkbenchCardDataProvider implements IWorkbenchCardDataProvider {

    private final IRuntimeService runtimeService;
    private final IMessageService messageService;

    public MetricsCardDataProvider(IRuntimeService runtimeService, IMessageService messageService) {
        this.runtimeService = runtimeService;
        this.messageService = messageService;
    }

    @Override
    public boolean supports(String cardCode) {
        return HomeWorkbenchConstants.CARD_METRICS.equals(cardCode);
    }

    @Override
    public WorkbenchCardDataVO loadData(WorkbenchCardDataReq req, WorkbenchCard card, RequestContext context) {
        long todoTotal = todoTotal(context);
        long doneTotal = doneTotal(context);
        long ccTotal = ccTotal(context);
        long messageTotal = messageTotal(context);

        WorkbenchCardDataVO vo = emptyData(card);
        vo.getSummary().put("todoTotal", todoTotal);
        vo.getSummary().put("doneTotal", doneTotal);
        vo.getSummary().put("ccTotal", ccTotal);
        vo.getSummary().put("messageTotal", messageTotal);
        vo.setItems(List.of(
                metricItem("todoTotal", "我的待办", todoTotal, "/workflow/todo", "blue", "lucide:check-square"),
                metricItem("doneTotal", "我的已办", doneTotal, "/workflow/done", "indigo", "lucide:check-check"),
                metricItem("ccTotal", "我的抄送", ccTotal, "/workflow/cc", "cyan", "lucide:send"),
                metricItem("messageTotal", "我的消息", messageTotal, "/message/inbox", "orange", "lucide:bell")
        ));
        return vo;
    }

    private long todoTotal(RequestContext context) {
        return runtimeService.countTodoTasks(context);
    }

    private long doneTotal(RequestContext context) {
        return runtimeService.countDoneTasks(context);
    }

    private long ccTotal(RequestContext context) {
        return runtimeService.countCcRecords(null, context);
    }

    private long messageTotal(RequestContext context) {
        return messageService.countInbox(context.getUsername(), MessageConstants.READ_STATUS_UNREAD, null);
    }

    private Map<String, Object> metricItem(String code, String title, long value, String targetPath,
            String tone, String icon) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", code);
        item.put("title", title);
        item.put("type", "metric");
        item.put("value", value);
        item.put("tone", tone);
        item.put("icon", icon);
        item.put("targetType", HomeWorkbenchConstants.TARGET_TYPE_ROUTE);
        item.put("targetPath", targetPath);
        return item;
    }
}
