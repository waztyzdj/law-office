package com.lawoffice.home.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.home.constant.HomeWorkbenchConstants;
import com.lawoffice.home.entity.WorkbenchCard;
import com.lawoffice.home.entity.WorkbenchUserCard;
import com.lawoffice.home.mapper.WorkbenchUserCardMapper;
import com.lawoffice.home.req.WorkbenchCardDataReq;
import com.lawoffice.home.req.WorkbenchLayoutSaveReq;
import com.lawoffice.home.service.IWorkbenchCardService;
import com.lawoffice.home.service.IWorkbenchUserCardService;
import com.lawoffice.home.vo.WorkbenchCardDataVO;
import com.lawoffice.home.vo.WorkbenchLayoutCardVO;
import com.lawoffice.home.vo.WorkbenchLayoutVO;
import com.lawoffice.home.vo.WorkbenchUserCardVO;
import com.lawoffice.system.mapper.PermissionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class WorkbenchUserCardServiceImpl
        extends AbstractHomeWorkbenchServiceImpl<WorkbenchUserCardMapper, WorkbenchUserCard, WorkbenchUserCardVO>
        implements IWorkbenchUserCardService {

    private static final int GRID_COLUMNS = 12;
    private static final int GRID_MAX_ROWS = 100;
    private static final int GRID_MIN_HEIGHT = 2;
    private static final int GRID_MIN_WIDTH = 3;

    private final IWorkbenchCardService workbenchCardService;
    private final WorkbenchCardDataProviderRegistry cardDataProviderRegistry;

    public WorkbenchUserCardServiceImpl(PermissionMapper permissionMapper,
            IWorkbenchCardService workbenchCardService,
            WorkbenchCardDataProviderRegistry cardDataProviderRegistry) {
        super(permissionMapper);
        this.workbenchCardService = workbenchCardService;
        this.cardDataProviderRegistry = cardDataProviderRegistry;
    }

    @Override
    public WorkbenchLayoutVO getCurrentLayout(RequestContext context) {
        String tenantId = requireTenantId(context);
        String userId = requireUserId(context);
        List<WorkbenchCard> cards = workbenchCardService.listAuthorizedEnabledCards(context);
        Map<String, WorkbenchUserCard> userCardMap = listCurrentUserCards(tenantId, userId).stream()
                .collect(Collectors.toMap(WorkbenchUserCard::getCardCode, item -> item, (left, right) -> left));

        WorkbenchLayoutVO layout = new WorkbenchLayoutVO();
        List<WorkbenchLayoutCardVO> layoutCards = cards.stream()
                .map(card -> buildLayoutCard(card, userCardMap.get(card.getCardCode())))
                .sorted(Comparator.comparing(WorkbenchLayoutCardVO::getSortNo, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(WorkbenchLayoutCardVO::getCardCode))
                .toList();
        layout.setCards(layoutCards.stream().filter(WorkbenchLayoutCardVO::getVisible).toList());
        layout.setHiddenCards(layoutCards.stream().filter(card -> !card.getVisible()).toList());
        return layout;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveCurrentLayout(WorkbenchLayoutSaveReq req, RequestContext context) {
        if (req == null || req.getCards() == null || req.getCards().isEmpty()) {
            throw new IllegalArgumentException("卡片布局不能为空");
        }
        String tenantId = requireTenantId(context);
        String userId = requireUserId(context);
        Map<String, WorkbenchCard> allowedCardMap = workbenchCardService.listAuthorizedEnabledCards(context).stream()
                .collect(Collectors.toMap(WorkbenchCard::getCardCode, item -> item, (left, right) -> left));
        Map<String, WorkbenchUserCard> existingMap = listCurrentUserCards(tenantId, userId).stream()
                .collect(Collectors.toMap(WorkbenchUserCard::getCardCode, item -> item, (left, right) -> left));

        for (WorkbenchLayoutSaveReq.Card item : req.getCards()) {
            String cardCode = trimToNull(item.getCardCode());
            WorkbenchCard card = allowedCardMap.get(cardCode);
            if (card == null) {
                throw new IllegalArgumentException("卡片不存在、已停用或无权配置：" + cardCode);
            }
            String size = StringUtils.hasText(item.getSize()) ? item.getSize().trim() : card.getDefaultSize();
            validateIn(size, "卡片尺寸不合法", HomeWorkbenchConstants.CARD_SIZES);
            GridLayout gridLayout = normalizeGridLayout(item, size);

            WorkbenchUserCard userCard = existingMap.get(cardCode);
            boolean create = userCard == null;
            if (create) {
                userCard = new WorkbenchUserCard();
                userCard.setId(newId());
                userCard.setTenantId(tenantId);
                userCard.setUserId(userId);
                userCard.setCardCode(cardCode);
            }
            userCard.setVisible(Boolean.FALSE.equals(item.getVisible()) ? 0 : 1);
            userCard.setSortNo(resolveLayoutSortNo(item, card, gridLayout));
            userCard.setSize(size);
            userCard.setGridX(gridLayout.x());
            userCard.setGridY(gridLayout.y());
            userCard.setGridW(gridLayout.w());
            userCard.setGridH(gridLayout.h());
            userCard.setConfigJson(null);
            fillCreateOrUpdate(userCard, context, create);
            if (create) {
                baseMapper.insert(userCard);
            } else {
                baseMapper.updateById(userCard);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetCurrentLayout(RequestContext context) {
        String tenantId = requireTenantId(context);
        String userId = requireUserId(context);
        QueryWrapper<WorkbenchUserCard> wrapper = activeTenantWrapper(tenantId);
        wrapper.eq("user_id", userId);
        // 用户布局是个人偏好缓存。物理清理可避免 delete_flag 唯一索引导致重复恢复默认后无法再次保存。
        baseMapper.delete(wrapper);
    }

    @Override
    public WorkbenchCardDataVO getCardData(WorkbenchCardDataReq req, RequestContext context) {
        if (req == null) {
            throw new IllegalArgumentException("卡片数据请求不能为空");
        }
        WorkbenchCard card = workbenchCardService.requireAuthorizedEnabledCard(req.getCardCode(), context);
        return cardDataProviderRegistry.requireProvider(card.getCardCode()).loadData(req, card, context);
    }

    private List<WorkbenchUserCard> listCurrentUserCards(String tenantId, String userId) {
        QueryWrapper<WorkbenchUserCard> wrapper = activeTenantWrapper(tenantId);
        wrapper.eq("user_id", userId);
        return baseMapper.selectList(wrapper);
    }

    private WorkbenchLayoutCardVO buildLayoutCard(WorkbenchCard card, WorkbenchUserCard userCard) {
        WorkbenchLayoutCardVO vo = new WorkbenchLayoutCardVO();
        vo.setCardCode(card.getCardCode());
        vo.setCardName(card.getCardName());
        vo.setComponentKey(card.getComponentKey());
        vo.setPermissionCode(card.getPermissionCode());
        vo.setVisible(userCard == null ? card.getDefaultVisible() != null && card.getDefaultVisible() == 1
                : userCard.getVisible() != null && userCard.getVisible() == 1);
        vo.setSortNo(userCard == null || userCard.getSortNo() == null ? card.getDefaultSort() : userCard.getSortNo());
        vo.setSize(userCard == null || !StringUtils.hasText(userCard.getSize()) ? card.getDefaultSize() : userCard.getSize());
        GridLayout gridLayout = userCard == null
                ? defaultGridLayout(vo.getSize(), vo.getSortNo())
                : normalizeGridLayout(userCard, vo.getSize(), vo.getSortNo());
        vo.setGridX(gridLayout.x());
        vo.setGridY(gridLayout.y());
        vo.setGridW(gridLayout.w());
        vo.setGridH(gridLayout.h());
        vo.setRefreshInterval(card.getDefaultRefreshInterval());
        vo.setConfigJson(card.getConfigJson());
        vo.setConfig(parseConfig(vo.getConfigJson()));
        vo.setSystemVisible(card.getDefaultVisible() != null && card.getDefaultVisible() == 1);
        vo.setUserCustomized(userCard != null);
        return vo;
    }

    /**
     * 前端拖拽结果仍需服务端归一化，防止越界布局写入后影响后续渲染。
     */
    private GridLayout normalizeGridLayout(WorkbenchLayoutSaveReq.Card item, String size) {
        int x = clamp(item.getGridX() == null ? 0 : item.getGridX(), 0, GRID_COLUMNS - GRID_MIN_WIDTH);
        int y = clamp(item.getGridY() == null ? 0 : item.getGridY(), 0, GRID_MAX_ROWS);
        int w = clamp(item.getGridW() == null ? defaultGridWidth(size) : item.getGridW(), GRID_MIN_WIDTH, GRID_COLUMNS);
        int h = clamp(item.getGridH() == null ? defaultGridHeight(size) : item.getGridH(), GRID_MIN_HEIGHT, GRID_MAX_ROWS);
        if (x + w > GRID_COLUMNS) {
            x = GRID_COLUMNS - w;
        }
        return new GridLayout(x, y, w, h);
    }

    /**
     * 历史布局可能缺少栅格字段，读取时回退到卡片尺寸对应的默认布局。
     */
    private GridLayout normalizeGridLayout(WorkbenchUserCard userCard, String size, Integer sortNo) {
        if (userCard.getGridW() == null || userCard.getGridH() == null) {
            return defaultGridLayout(size, sortNo);
        }
        int x = clamp(userCard.getGridX() == null ? 0 : userCard.getGridX(), 0, GRID_COLUMNS - GRID_MIN_WIDTH);
        int y = clamp(userCard.getGridY() == null ? 0 : userCard.getGridY(), 0, GRID_MAX_ROWS);
        int w = clamp(userCard.getGridW(), GRID_MIN_WIDTH, GRID_COLUMNS);
        int h = clamp(userCard.getGridH(), GRID_MIN_HEIGHT, GRID_MAX_ROWS);
        if (x + w > GRID_COLUMNS) {
            x = GRID_COLUMNS - w;
        }
        return new GridLayout(x, y, w, h);
    }

    /**
     * 默认布局按卡片默认排序顺序铺到 12 列栅格中，保证无个人布局时也能稳定呈现。
     */
    private GridLayout defaultGridLayout(String size, Integer sortNo) {
        int width = defaultGridWidth(size);
        int index = Math.max((sortNo == null ? 10 : sortNo) / 10 - 1, 0);
        int x = (index * width) % GRID_COLUMNS;
        int y = ((index * width) / GRID_COLUMNS) * defaultGridHeight(size);
        return new GridLayout(x, y, width, defaultGridHeight(size));
    }

    /**
     * sortNo 保留为旧表格排序兼容字段，实际值由栅格位置派生，不再单独由前端维护。
     */
    private Integer resolveLayoutSortNo(WorkbenchLayoutSaveReq.Card item, WorkbenchCard card, GridLayout gridLayout) {
        if (item.getGridX() == null || item.getGridY() == null) {
            return card.getDefaultSort();
        }
        return (gridLayout.y() * GRID_COLUMNS + gridLayout.x() + 1) * 10;
    }

    private int defaultGridWidth(String size) {
        if (HomeWorkbenchConstants.SIZE_FULL.equals(size)) {
            return 12;
        }
        if (HomeWorkbenchConstants.SIZE_LARGE.equals(size)) {
            return 6;
        }
        if (HomeWorkbenchConstants.SIZE_SMALL.equals(size)) {
            return 3;
        }
        return 4;
    }

    private int defaultGridHeight(String size) {
        if (HomeWorkbenchConstants.SIZE_FULL.equals(size)) {
            return 4;
        }
        if (HomeWorkbenchConstants.SIZE_LARGE.equals(size)) {
            return 4;
        }
        return 3;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record GridLayout(int x, int y, int w, int h) {
    }
}
