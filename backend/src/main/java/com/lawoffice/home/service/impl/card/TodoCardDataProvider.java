package com.lawoffice.home.service.impl.card;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.home.constant.HomeWorkbenchConstants;
import com.lawoffice.home.entity.WorkbenchCard;
import com.lawoffice.home.req.WorkbenchCardDataReq;
import com.lawoffice.home.service.IWorkbenchCardDataProvider;
import com.lawoffice.home.vo.WorkbenchCardDataVO;
import com.lawoffice.workflow.req.TaskPageReq;
import com.lawoffice.workflow.service.IRuntimeService;
import com.lawoffice.workflow.vo.RuntimeTaskVO;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class TodoCardDataProvider extends AbstractWorkbenchCardDataProvider implements IWorkbenchCardDataProvider {

    private final IRuntimeService runtimeService;

    public TodoCardDataProvider(IRuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @Override
    public boolean supports(String cardCode) {
        return HomeWorkbenchConstants.CARD_TODO.equals(cardCode);
    }

    @Override
    public WorkbenchCardDataVO loadData(WorkbenchCardDataReq req, WorkbenchCard card, RequestContext context) {
        int fetchLimit = resolveListFetchLimit();
        PageVO<RuntimeTaskVO> todoPage = loadTodoPage(fetchLimit, context);
        PageVO<RuntimeTaskVO> donePage = loadDonePage(fetchLimit, context);

        WorkbenchCardDataVO vo = emptyData(card);
        vo.getSummary().put("todoTotal", total(todoPage));
        vo.getSummary().put("doneTotal", total(donePage));
        long overdue = todoPage == null || todoPage.getRecords() == null ? 0 : todoPage.getRecords().stream()
                .filter(task -> task.getDueTime() != null && task.getDueTime().isBefore(LocalDateTime.now()))
                .count();
        vo.getSummary().put("urgent", overdue);
        List<Map<String, Object>> items = new ArrayList<>();
        items.addAll(mapTodoItems(todoPage));
        items.addAll(mapDoneItems(donePage));
        vo.setItems(items);
        return vo;
    }

    private PageVO<RuntimeTaskVO> loadTodoPage(int limit, RequestContext context) {
        TaskPageReq pageReq = new TaskPageReq();
        pageReq.setPageNum(1);
        pageReq.setPageSize(limit);
        pageReq.setSortField("createTime");
        pageReq.setSortOrder("desc");
        BaseResult<PageVO<RuntimeTaskVO>> result = runtimeService.pageTodo(pageReq, context);
        if (result.getCode() == null || result.getCode() != 200) {
            throw new IllegalArgumentException(result.getMessage());
        }
        return result.getData();
    }

    private PageVO<RuntimeTaskVO> loadDonePage(int limit, RequestContext context) {
        TaskPageReq pageReq = new TaskPageReq();
        pageReq.setPageNum(1);
        pageReq.setPageSize(limit);
        pageReq.setSortField("completeTime");
        pageReq.setSortOrder("desc");
        BaseResult<PageVO<RuntimeTaskVO>> result = runtimeService.pageDone(pageReq, context);
        if (result.getCode() == null || result.getCode() != 200) {
            throw new IllegalArgumentException(result.getMessage());
        }
        return result.getData();
    }

    private List<Map<String, Object>> mapTodoItems(PageVO<RuntimeTaskVO> page) {
        if (page == null || page.getRecords() == null) {
            return List.of();
        }
        return page.getRecords().stream()
                .map(task -> {
                    Map<String, Object> item = item(
                            task.getId(),
                            task.getInstanceTitle(),
                            "todo",
                            task.getDueTime() != null && task.getDueTime().isBefore(LocalDateTime.now()) ? "overdue" : task.getStatus(),
                            task.getStartTime(),
                            HomeWorkbenchConstants.TARGET_TYPE_ROUTE,
                            "/workflow/todo",
                            task.getId());
                    item.put("instanceId", task.getProcessInstanceId());
                    return item;
                })
                .toList();
    }

    private List<Map<String, Object>> mapDoneItems(PageVO<RuntimeTaskVO> page) {
        if (page == null || page.getRecords() == null) {
            return List.of();
        }
        return page.getRecords().stream()
                .map(task -> {
                    Map<String, Object> item = item(
                            task.getId(),
                            task.getInstanceTitle(),
                            "done",
                            task.getStatus(),
                            task.getCompleteTime(),
                            HomeWorkbenchConstants.TARGET_TYPE_ROUTE,
                            "/workflow/done",
                            task.getProcessInstanceId());
                    item.put("instanceId", task.getProcessInstanceId());
                    return item;
                })
                .toList();
    }
}
