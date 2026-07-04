package com.lawoffice.home.service.impl.card;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.home.constant.HomeWorkbenchConstants;
import com.lawoffice.home.entity.WorkbenchCard;
import com.lawoffice.home.req.WorkbenchCardDataReq;
import com.lawoffice.home.req.WorkbenchRecentPageReq;
import com.lawoffice.home.service.IWorkbenchCardDataProvider;
import com.lawoffice.home.service.IWorkbenchRecentRecordService;
import com.lawoffice.home.vo.WorkbenchCardDataVO;
import com.lawoffice.home.vo.WorkbenchRecentRecordVO;
import org.springframework.stereotype.Component;

@Component
public class RecentCardDataProvider extends AbstractWorkbenchCardDataProvider implements IWorkbenchCardDataProvider {

    private final IWorkbenchRecentRecordService recentRecordService;

    public RecentCardDataProvider(IWorkbenchRecentRecordService recentRecordService) {
        this.recentRecordService = recentRecordService;
    }

    @Override
    public boolean supports(String cardCode) {
        return HomeWorkbenchConstants.CARD_RECENT.equals(cardCode);
    }

    @Override
    public WorkbenchCardDataVO loadData(WorkbenchCardDataReq req, WorkbenchCard card, RequestContext context) {
        WorkbenchRecentPageReq pageReq = new WorkbenchRecentPageReq();
        pageReq.setPageNum(1);
        pageReq.setPageSize(resolveListFetchLimit());
        PageVO<WorkbenchRecentRecordVO> page = recentRecordService.pageCurrentUserRecords(pageReq, context);
        WorkbenchCardDataVO vo = emptyData(card);
        vo.getSummary().put("total", total(page));
        if (page != null && page.getRecords() != null) {
            vo.setItems(page.getRecords().stream()
                    .map(record -> item(
                            record.getId(),
                            record.getTitle(),
                            record.getRecordType(),
                            "visited",
                            record.getLastVisitTime(),
                            record.getTargetType(),
                            record.getTargetPath(),
                            record.getBizId()))
                    .toList());
        }
        return vo;
    }
}
