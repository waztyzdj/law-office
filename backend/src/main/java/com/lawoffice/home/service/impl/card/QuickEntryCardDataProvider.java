package com.lawoffice.home.service.impl.card;

import cn.hutool.core.bean.BeanUtil;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.home.constant.HomeWorkbenchConstants;
import com.lawoffice.home.entity.WorkbenchCard;
import com.lawoffice.home.req.WorkbenchCardDataReq;
import com.lawoffice.home.req.WorkbenchQuickEntryListReq;
import com.lawoffice.home.service.IWorkbenchCardDataProvider;
import com.lawoffice.home.service.IWorkbenchQuickEntryService;
import com.lawoffice.home.vo.WorkbenchCardDataVO;
import com.lawoffice.home.vo.WorkbenchQuickEntryVO;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class QuickEntryCardDataProvider extends AbstractWorkbenchCardDataProvider implements IWorkbenchCardDataProvider {

    private final IWorkbenchQuickEntryService quickEntryService;

    public QuickEntryCardDataProvider(IWorkbenchQuickEntryService quickEntryService) {
        this.quickEntryService = quickEntryService;
    }

    @Override
    public boolean supports(String cardCode) {
        return HomeWorkbenchConstants.CARD_QUICK_ENTRY.equals(cardCode);
    }

    @Override
    public WorkbenchCardDataVO loadData(WorkbenchCardDataReq req, WorkbenchCard card, RequestContext context) {
        WorkbenchQuickEntryListReq listReq = new WorkbenchQuickEntryListReq();
        listReq.setIncludeSystem(Boolean.TRUE);
        var entries = quickEntryService.listCurrentUserEntries(listReq, context).getEntries();
        WorkbenchCardDataVO vo = emptyData(card);
        vo.getSummary().put("total", entries.size());
        Integer limit = resolveQuickEntryLimit(req, card);
        var entryStream = entries.stream();
        if (limit != null) {
            entryStream = entryStream.limit(limit);
        }
        vo.setItems(entryStream.map(this::toItem).toList());
        return vo;
    }

    private Map<String, Object> toItem(WorkbenchQuickEntryVO entry) {
        Map<String, Object> item = BeanUtil.beanToMap(entry);
        item.put("id", entry.getId());
        item.put("title", entry.getEntryName());
        item.put("type", "quick-entry");
        item.put("status", entry.getStatus());
        item.put("targetType", entry.getEntryType());
        item.put("targetPath", entry.getPath());
        item.put("bizId", entry.getMenuId());
        return item;
    }
}
