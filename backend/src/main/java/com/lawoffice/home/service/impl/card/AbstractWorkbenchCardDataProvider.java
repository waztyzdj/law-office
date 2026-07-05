package com.lawoffice.home.service.impl.card;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.home.entity.WorkbenchCard;
import com.lawoffice.home.req.WorkbenchCardDataReq;
import com.lawoffice.home.vo.WorkbenchCardDataVO;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

abstract class AbstractWorkbenchCardDataProvider {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int SUCCESS_CODE = 200;
    private static final int DEFAULT_LIMIT = 8;
    private static final int LIST_FETCH_LIMIT = 200;
    private static final int MAX_QUICK_ENTRY_LIMIT = 99;

    protected WorkbenchCardDataVO emptyData(WorkbenchCard card) {
        WorkbenchCardDataVO vo = new WorkbenchCardDataVO();
        vo.setCardCode(card.getCardCode());
        return vo;
    }

    protected Integer resolveQuickEntryLimit(WorkbenchCardDataReq req, WorkbenchCard card) {
        Integer limit = req == null ? null : req.getLimit();
        if (limit == null) {
            limit = readCardLimit(card);
        }
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        if (limit <= 0) {
            return null;
        }
        return Math.min(limit, MAX_QUICK_ENTRY_LIMIT);
    }

    protected int resolveListFetchLimit() {
        return LIST_FETCH_LIMIT;
    }

    private Integer readCardLimit(WorkbenchCard card) {
        if (card == null || !StringUtils.hasText(card.getConfigJson())) {
            return null;
        }
        try {
            JsonNode limitNode = OBJECT_MAPPER.readTree(card.getConfigJson()).get("limit");
            if (limitNode == null || !limitNode.canConvertToInt()) {
                return null;
            }
            return limitNode.asInt();
        } catch (Exception e) {
            return null;
        }
    }

    protected <T> long total(PageVO<T> page) {
        return page == null ? 0L : page.getTotal();
    }

    protected <T> PageVO<T> requireSuccessPage(BaseResult<PageVO<T>> result) {
        if (result == null || result.getCode() == null || result.getCode() != SUCCESS_CODE) {
            throw new IllegalArgumentException(result == null ? "卡片数据加载失败" : result.getMessage());
        }
        return result.getData();
    }

    protected <T> long totalOrZero(BaseResult<PageVO<T>> result) {
        return result != null && result.getCode() != null && result.getCode() == SUCCESS_CODE
                ? total(result.getData())
                : 0L;
    }

    protected Map<String, Object> item(String id, String title, String type, String status,
            LocalDateTime occurTime, String targetType, String targetPath, String bizId) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("title", title);
        item.put("type", type);
        item.put("status", status);
        item.put("occurTime", occurTime);
        item.put("targetType", targetType);
        item.put("targetPath", targetPath);
        item.put("bizId", bizId);
        return item;
    }
}
