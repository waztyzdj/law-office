package com.lawoffice.home.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.home.constant.HomeWorkbenchConstants;
import com.lawoffice.home.entity.WorkbenchCard;
import com.lawoffice.home.mapper.WorkbenchCardMapper;
import com.lawoffice.home.req.WorkbenchCardPageReq;
import com.lawoffice.home.req.WorkbenchCardReq;
import com.lawoffice.home.req.WorkbenchCardSortReq;
import com.lawoffice.home.service.IWorkbenchCardService;
import com.lawoffice.home.vo.WorkbenchCardVO;
import com.lawoffice.system.mapper.PermissionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class WorkbenchCardServiceImpl
        extends AbstractHomeWorkbenchServiceImpl<WorkbenchCardMapper, WorkbenchCard, WorkbenchCardVO>
        implements IWorkbenchCardService {

    public WorkbenchCardServiceImpl(PermissionMapper permissionMapper) {
        super(permissionMapper);
    }

    @Override
    public PageVO<WorkbenchCardVO> pageCards(WorkbenchCardPageReq req, RequestContext context) {
        String tenantId = requireTenantId(context);
        WorkbenchCardPageReq safeReq = req == null ? new WorkbenchCardPageReq() : req;
        QueryWrapper<WorkbenchCard> wrapper = activeTenantWrapper(tenantId);
        if (StringUtils.hasText(safeReq.getCardCode())) {
            wrapper.like("card_code", safeReq.getCardCode().trim());
        }
        if (StringUtils.hasText(safeReq.getCardName())) {
            wrapper.like("card_name", safeReq.getCardName().trim());
        }
        if (StringUtils.hasText(safeReq.getComponentKey())) {
            wrapper.like("component_key", safeReq.getComponentKey().trim());
        }
        if (StringUtils.hasText(safeReq.getStatus())) {
            wrapper.eq("status", safeReq.getStatus().trim());
        }
        wrapper.orderByAsc("default_sort").orderByDesc("create_time");

        Page<WorkbenchCard> page = new Page<>(pageNum(safeReq), pageSize(safeReq));
        Page<WorkbenchCard> result = this.page(page, wrapper);
        return new PageVO<>(
                result.getRecords().stream().map(this::toVO).toList(),
                result.getTotal(),
                result.getCurrent(),
                result.getSize());
    }

    @Override
    public WorkbenchCardVO getCardDetail(String id, RequestContext context) {
        WorkbenchCard card = requireCurrentCard(id, requireTenantId(context));
        return toVO(card);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkbenchCardVO saveCard(WorkbenchCardReq req, RequestContext context) {
        if (req == null) {
            throw new IllegalArgumentException("卡片配置不能为空");
        }
        String tenantId = requireTenantId(context);
        boolean create = !StringUtils.hasText(req.getId());
        WorkbenchCard card = create ? new WorkbenchCard() : requireCurrentCard(req.getId(), tenantId);

        card.setTenantId(tenantId);
        card.setCardCode(trimToNull(req.getCardCode()));
        card.setCardName(trimToNull(req.getCardName()));
        card.setComponentKey(trimToNull(req.getComponentKey()));
        card.setPermissionCode(trimToNull(req.getPermissionCode()));
        card.setStatus(defaultStatus(trimToNull(req.getStatus())));
        card.setDefaultVisible(req.getDefaultVisible() == null ? 1 : req.getDefaultVisible());
        card.setDefaultSort(req.getDefaultSort() == null ? 0 : req.getDefaultSort());
        card.setDefaultSize(defaultSize(trimToNull(req.getDefaultSize())));
        card.setDefaultRefreshInterval(req.getDefaultRefreshInterval());
        card.setConfigJson(normalizeConfigJson(req.getConfigJson(), req.getConfig(), "卡片扩展配置"));
        card.setRemark(trimToNull(req.getRemark()));

        validateCard(card);
        validateUniqueCode(card);
        validatePermissionCode(card.getPermissionCode());
        fillCreateOrUpdate(card, context, create);
        if (create) {
            card.setId(newId());
            baseMapper.insert(card);
        } else {
            baseMapper.updateById(card);
        }
        return toVO(card);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String id, String status, RequestContext context) {
        String tenantId = requireTenantId(context);
        WorkbenchCard current = requireCurrentCard(id, tenantId);
        String normalizedStatus = trimToNull(status);
        validateIn(normalizedStatus, "卡片状态不合法", HomeWorkbenchConstants.STATUSES);

        WorkbenchCard update = new WorkbenchCard();
        update.setId(current.getId());
        update.setStatus(normalizedStatus);
        fillUpdate(update, context);
        baseMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSort(WorkbenchCardSortReq req, RequestContext context) {
        if (req == null || req.getItems() == null || req.getItems().isEmpty()) {
            throw new IllegalArgumentException("排序项不能为空");
        }
        String tenantId = requireTenantId(context);
        for (WorkbenchCardSortReq.Item item : req.getItems()) {
            WorkbenchCard current = requireCurrentCard(item.getId(), tenantId);
            WorkbenchCard update = new WorkbenchCard();
            update.setId(current.getId());
            update.setDefaultSort(item.getDefaultSort() == null ? 0 : item.getDefaultSort());
            fillUpdate(update, context);
            baseMapper.updateById(update);
        }
    }

    @Override
    public List<WorkbenchCard> listAuthorizedEnabledCards(RequestContext context) {
        String tenantId = requireTenantId(context);
        QueryWrapper<WorkbenchCard> wrapper = activeTenantWrapper(tenantId);
        wrapper.eq("status", HomeWorkbenchConstants.STATUS_ENABLED)
                .orderByAsc("default_sort")
                .orderByAsc("create_time");
        return baseMapper.selectList(wrapper).stream()
                .filter(card -> hasPermission(card.getPermissionCode()))
                .toList();
    }

    @Override
    public WorkbenchCard requireAuthorizedEnabledCard(String cardCode, RequestContext context) {
        String tenantId = requireTenantId(context);
        String normalizedCode = trimToNull(cardCode);
        requireText(normalizedCode, "卡片编码不能为空");
        QueryWrapper<WorkbenchCard> wrapper = activeTenantWrapper(tenantId);
        wrapper.eq("card_code", normalizedCode)
                .eq("status", HomeWorkbenchConstants.STATUS_ENABLED);
        WorkbenchCard card = baseMapper.selectOne(wrapper);
        if (card == null) {
            throw new IllegalArgumentException("卡片不存在或已停用");
        }
        if (!hasPermission(card.getPermissionCode())) {
            throw new IllegalArgumentException("无权访问该卡片");
        }
        return card;
    }

    private void validateCard(WorkbenchCard card) {
        requireText(card.getCardCode(), "卡片编码不能为空");
        requireText(card.getCardName(), "卡片名称不能为空");
        requireText(card.getComponentKey(), "卡片组件不能为空");
        validateIn(card.getStatus(), "卡片状态不合法", HomeWorkbenchConstants.STATUSES);
        validateIn(card.getDefaultSize(), "卡片默认尺寸不合法", HomeWorkbenchConstants.CARD_SIZES);
        if (card.getDefaultVisible() == null || (card.getDefaultVisible() != 0 && card.getDefaultVisible() != 1)) {
            throw new IllegalArgumentException("默认显示状态不合法");
        }
        if (card.getDefaultRefreshInterval() != null && card.getDefaultRefreshInterval() < 30) {
            throw new IllegalArgumentException("刷新间隔不能小于30秒");
        }
    }

    private void validateUniqueCode(WorkbenchCard card) {
        QueryWrapper<WorkbenchCard> wrapper = activeTenantWrapper(card.getTenantId());
        wrapper.eq("card_code", card.getCardCode());
        if (StringUtils.hasText(card.getId())) {
            wrapper.ne("id", card.getId());
        }
        if (baseMapper.selectCount(wrapper) > 0) {
            throw new IllegalArgumentException("同一租户下卡片编码不能重复");
        }
    }

    private WorkbenchCard requireCurrentCard(String id, String tenantId) {
        requireText(id, "卡片ID不能为空");
        QueryWrapper<WorkbenchCard> wrapper = activeTenantWrapper(tenantId);
        wrapper.eq("id", id);
        WorkbenchCard card = baseMapper.selectOne(wrapper);
        if (card == null) {
            throw new IllegalArgumentException("卡片配置不存在");
        }
        return card;
    }

    private WorkbenchCardVO toVO(WorkbenchCard card) {
        WorkbenchCardVO vo = BeanUtil.toBean(card, WorkbenchCardVO.class);
        vo.setConfig(parseConfig(card.getConfigJson()));
        return vo;
    }
}
