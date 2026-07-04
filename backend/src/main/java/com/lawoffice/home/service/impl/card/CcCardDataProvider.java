package com.lawoffice.home.service.impl.card;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.home.constant.HomeWorkbenchConstants;
import com.lawoffice.home.entity.WorkbenchCard;
import com.lawoffice.home.req.WorkbenchCardDataReq;
import com.lawoffice.home.service.IWorkbenchCardDataProvider;
import com.lawoffice.home.vo.WorkbenchCardDataVO;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.req.CcPageReq;
import com.lawoffice.workflow.service.IRuntimeService;
import com.lawoffice.workflow.vo.CcRecordVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class CcCardDataProvider extends AbstractWorkbenchCardDataProvider implements IWorkbenchCardDataProvider {

    private final IRuntimeService runtimeService;

    public CcCardDataProvider(IRuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    @Override
    public boolean supports(String cardCode) {
        return HomeWorkbenchConstants.CARD_CC.equals(cardCode);
    }

    @Override
    public WorkbenchCardDataVO loadData(WorkbenchCardDataReq req, WorkbenchCard card, RequestContext context) {
        int fetchLimit = resolveListFetchLimit();
        PageVO<CcRecordVO> unreadPage = loadCcPage(fetchLimit, WorkflowConstants.CcStatus.UNREAD, context);
        PageVO<CcRecordVO> readPage = loadCcPage(fetchLimit, WorkflowConstants.CcStatus.READ, context);

        WorkbenchCardDataVO vo = emptyData(card);
        vo.getSummary().put("unreadTotal", total(unreadPage));
        vo.getSummary().put("readTotal", total(readPage));
        List<Map<String, Object>> items = new ArrayList<>();
        items.addAll(mapCcItems(unreadPage, "unread-cc"));
        items.addAll(mapCcItems(readPage, "read-cc"));
        vo.setItems(items);
        return vo;
    }

    private PageVO<CcRecordVO> loadCcPage(int limit, String status, RequestContext context) {
        CcPageReq pageReq = new CcPageReq();
        pageReq.setPageNum(1);
        pageReq.setPageSize(limit);
        pageReq.setStatus(status);
        BaseResult<PageVO<CcRecordVO>> result = runtimeService.pageCc(pageReq, context);
        if (result.getCode() == null || result.getCode() != 200) {
            throw new IllegalArgumentException(result.getMessage());
        }
        return result.getData();
    }

    private List<Map<String, Object>> mapCcItems(PageVO<CcRecordVO> page, String type) {
        if (page == null || page.getRecords() == null) {
            return List.of();
        }
        return page.getRecords().stream()
                .map(record -> {
                    Map<String, Object> item = item(
                            record.getId(),
                            record.getInstanceTitle(),
                            type,
                            record.getStatus(),
                            record.getCreateTime(),
                            HomeWorkbenchConstants.TARGET_TYPE_ROUTE,
                            "/workflow/cc",
                            record.getProcessInstanceId());
                    item.put("instanceId", record.getProcessInstanceId());
                    return item;
                })
                .toList();
    }
}
