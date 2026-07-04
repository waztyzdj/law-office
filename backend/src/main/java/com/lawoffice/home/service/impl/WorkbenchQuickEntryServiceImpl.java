package com.lawoffice.home.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.home.constant.HomeWorkbenchConstants;
import com.lawoffice.home.entity.WorkbenchQuickEntry;
import com.lawoffice.home.mapper.WorkbenchQuickEntryMapper;
import com.lawoffice.home.req.WorkbenchQuickEntryListReq;
import com.lawoffice.home.req.WorkbenchQuickEntryPageReq;
import com.lawoffice.home.req.WorkbenchQuickEntryReq;
import com.lawoffice.home.service.IWorkbenchQuickEntryService;
import com.lawoffice.home.vo.WorkbenchQuickEntryListVO;
import com.lawoffice.home.vo.WorkbenchQuickEntryVO;
import com.lawoffice.system.mapper.PermissionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Comparator;
import java.util.List;

@Service
public class WorkbenchQuickEntryServiceImpl
        extends AbstractHomeWorkbenchServiceImpl<WorkbenchQuickEntryMapper, WorkbenchQuickEntry, WorkbenchQuickEntryVO>
        implements IWorkbenchQuickEntryService {

    private static final String LEGACY_WORKSPACE_ENTRY_CODE = "workspace";
    private static final String LEGACY_WORKSPACE_ENTRY_NAME = "工作台";

    public WorkbenchQuickEntryServiceImpl(PermissionMapper permissionMapper) {
        super(permissionMapper);
    }

    @Override
    public PageVO<WorkbenchQuickEntryVO> pageSystemEntries(WorkbenchQuickEntryPageReq req, RequestContext context) {
        String tenantId = requireTenantId(context);
        WorkbenchQuickEntryPageReq safeReq = req == null ? new WorkbenchQuickEntryPageReq() : req;
        QueryWrapper<WorkbenchQuickEntry> wrapper = activeTenantWrapper(tenantId);
        wrapper.eq("owner_type", HomeWorkbenchConstants.OWNER_SYSTEM)
                .eq("owner_user_id", HomeWorkbenchConstants.SYSTEM_OWNER_USER_ID);
        if (StringUtils.hasText(safeReq.getEntryCode())) {
            wrapper.like("entry_code", safeReq.getEntryCode().trim());
        }
        if (StringUtils.hasText(safeReq.getEntryName())) {
            wrapper.like("entry_name", safeReq.getEntryName().trim());
        }
        if (StringUtils.hasText(safeReq.getEntryType())) {
            wrapper.eq("entry_type", safeReq.getEntryType().trim());
        }
        if (StringUtils.hasText(safeReq.getStatus())) {
            wrapper.eq("status", safeReq.getStatus().trim());
        }
        wrapper.orderByAsc("sort_no").orderByDesc("create_time");

        Page<WorkbenchQuickEntry> page = new Page<>(pageNum(safeReq), pageSize(safeReq));
        Page<WorkbenchQuickEntry> result = this.page(page, wrapper);
        return new PageVO<>(
                result.getRecords().stream().map(this::toVO).toList(),
                result.getTotal(),
                result.getCurrent(),
                result.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkbenchQuickEntryVO saveSystemEntry(WorkbenchQuickEntryReq req, RequestContext context) {
        WorkbenchQuickEntry entry = saveEntry(req, context, HomeWorkbenchConstants.OWNER_SYSTEM,
                HomeWorkbenchConstants.SYSTEM_OWNER_USER_ID, true);
        return toVO(entry);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSystemStatus(String id, String status, RequestContext context) {
        String tenantId = requireTenantId(context);
        WorkbenchQuickEntry current = requireEntry(id, tenantId,
                HomeWorkbenchConstants.OWNER_SYSTEM, HomeWorkbenchConstants.SYSTEM_OWNER_USER_ID);
        String normalizedStatus = trimToNull(status);
        validateIn(normalizedStatus, "快捷菜单状态不合法", HomeWorkbenchConstants.STATUSES);

        WorkbenchQuickEntry update = new WorkbenchQuickEntry();
        update.setId(current.getId());
        update.setStatus(normalizedStatus);
        fillUpdate(update, context);
        baseMapper.updateById(update);
    }

    @Override
    public WorkbenchQuickEntryListVO listCurrentUserEntries(WorkbenchQuickEntryListReq req, RequestContext context) {
        String tenantId = requireTenantId(context);
        String userId = requireUserId(context);
        boolean includeSystem = req == null || !Boolean.FALSE.equals(req.getIncludeSystem());

        List<WorkbenchQuickEntry> entries = includeSystem
                ? queryEnabledEntries(tenantId, HomeWorkbenchConstants.OWNER_SYSTEM, HomeWorkbenchConstants.SYSTEM_OWNER_USER_ID)
                : new java.util.ArrayList<>();
        entries.addAll(queryEnabledEntries(tenantId, HomeWorkbenchConstants.OWNER_USER, userId));

        WorkbenchQuickEntryListVO vo = new WorkbenchQuickEntryListVO();
        vo.setEntries(entries.stream()
                .filter(entry -> !isLegacySystemWorkspaceEntry(entry))
                .filter(entry -> hasMenuAccess(entry.getMenuId(), entry.getPermissionCode()))
                .sorted(Comparator.comparing(WorkbenchQuickEntry::getSortNo, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(WorkbenchQuickEntry::getEntryCode))
                .map(this::toVO)
                .toList());
        return vo;
    }

    private boolean isLegacySystemWorkspaceEntry(WorkbenchQuickEntry entry) {
        return HomeWorkbenchConstants.OWNER_SYSTEM.equals(entry.getOwnerType())
                && HomeWorkbenchConstants.SYSTEM_OWNER_USER_ID.equals(entry.getOwnerUserId())
                && LEGACY_WORKSPACE_ENTRY_CODE.equals(entry.getEntryCode())
                && LEGACY_WORKSPACE_ENTRY_NAME.equals(entry.getEntryName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkbenchQuickEntryVO saveCurrentUserEntry(WorkbenchQuickEntryReq req, RequestContext context) {
        String userId = requireUserId(context);
        WorkbenchQuickEntry entry = saveEntry(req, context, HomeWorkbenchConstants.OWNER_USER, userId, false);
        return toVO(entry);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCurrentUserEntry(String id, RequestContext context) {
        String tenantId = requireTenantId(context);
        String userId = requireUserId(context);
        WorkbenchQuickEntry current = requireEntry(id, tenantId, HomeWorkbenchConstants.OWNER_USER, userId);
        QueryWrapper<WorkbenchQuickEntry> wrapper = activeTenantWrapper(tenantId);
        wrapper.eq("id", current.getId())
                .eq("owner_type", HomeWorkbenchConstants.OWNER_USER)
                .eq("owner_user_id", userId);
        // 个人快捷菜单属于用户偏好数据，物理删除可避免 delete_flag 唯一索引影响同编码重建。
        baseMapper.delete(wrapper);
    }

    private WorkbenchQuickEntry saveEntry(WorkbenchQuickEntryReq req, RequestContext context,
            String ownerType, String ownerUserId, boolean systemEntry) {
        if (req == null) {
            throw new IllegalArgumentException("快捷菜单不能为空");
        }
        String tenantId = requireTenantId(context);
        boolean create = !StringUtils.hasText(req.getId());
        WorkbenchQuickEntry entry = create ? new WorkbenchQuickEntry() : requireEntry(req.getId(), tenantId, ownerType, ownerUserId);

        entry.setTenantId(tenantId);
        entry.setOwnerType(ownerType);
        entry.setOwnerUserId(ownerUserId);
        entry.setEntryCode(normalizeEntryCode(req.getEntryCode(), entry, systemEntry));
        entry.setEntryName(trimToNull(req.getEntryName()));
        entry.setEntryType(trimToNull(req.getEntryType()));
        entry.setMenuId(trimToNull(req.getMenuId()));
        entry.setPath(trimToNull(req.getPath()));
        entry.setPermissionCode(trimToNull(req.getPermissionCode()));
        entry.setIcon(trimToNull(req.getIcon()));
        entry.setSortNo(req.getSortNo() == null ? 0 : req.getSortNo());
        entry.setStatus(defaultStatus(trimToNull(req.getStatus())));
        entry.setConfigJson(normalizeConfigJson(req.getConfigJson(), req.getConfig(), "快捷菜单扩展配置"));

        validateEntry(entry, systemEntry);
        validateUniqueEntryCode(entry);
        fillCreateOrUpdate(entry, context, create);
        if (create) {
            entry.setId(newId());
            baseMapper.insert(entry);
        } else {
            baseMapper.updateById(entry);
        }
        return entry;
    }

    private String normalizeEntryCode(String reqCode, WorkbenchQuickEntry current, boolean systemEntry) {
        String code = trimToNull(reqCode);
        if (StringUtils.hasText(code)) {
            return code;
        }
        if (!systemEntry && StringUtils.hasText(current.getEntryCode())) {
            return current.getEntryCode();
        }
        if (systemEntry) {
            throw new IllegalArgumentException("系统快捷菜单编码不能为空");
        }
        return "user-" + newId();
    }

    private void validateEntry(WorkbenchQuickEntry entry, boolean systemEntry) {
        requireText(entry.getEntryCode(), "入口编码不能为空");
        requireText(entry.getEntryName(), "入口名称不能为空");
        requireText(entry.getEntryType(), "入口类型不能为空");
        validateIn(entry.getEntryType(), "入口类型不合法", HomeWorkbenchConstants.ENTRY_TYPES);
        validateIn(entry.getStatus(), "快捷菜单状态不合法", HomeWorkbenchConstants.STATUSES);
        validatePermissionCode(entry.getPermissionCode());
        requireActiveMenu(entry.getMenuId());
        if (!systemEntry
                && !HomeWorkbenchConstants.ENTRY_TYPE_MENU.equals(entry.getEntryType())
                && !HomeWorkbenchConstants.ENTRY_TYPE_LINK.equals(entry.getEntryType())) {
            throw new IllegalArgumentException("个人快捷菜单仅支持内部菜单或外部链接");
        }
        if (HomeWorkbenchConstants.ENTRY_TYPE_MENU.equals(entry.getEntryType())
                && !StringUtils.hasText(entry.getMenuId())
                && !StringUtils.hasText(entry.getPath())) {
            throw new IllegalArgumentException("菜单入口必须绑定菜单或内部路由");
        }
        if (HomeWorkbenchConstants.ENTRY_TYPE_LINK.equals(entry.getEntryType())) {
            validateExternalUrl(entry.getPath());
        }
        if (!systemEntry
                && HomeWorkbenchConstants.ENTRY_TYPE_MENU.equals(entry.getEntryType())
                && !StringUtils.hasText(entry.getMenuId())) {
            throw new IllegalArgumentException("个人内部菜单必须从已授权菜单中选择");
        }
        if (!systemEntry && !hasMenuAccess(entry.getMenuId(), entry.getPermissionCode())) {
            throw new IllegalArgumentException("无权添加该快捷菜单");
        }
    }

    /**
     * 外部链接只允许 http/https，避免把 javascript: 等危险协议保存为可点击入口。
     */
    private void validateExternalUrl(String url) {
        requireText(url, "外部链接地址不能为空");
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (!StringUtils.hasText(host)
                    || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException("外部链接必须以 http:// 或 https:// 开头");
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("外部链接必须以 http:// 或 https:// 开头");
        }
    }

    private void validateUniqueEntryCode(WorkbenchQuickEntry entry) {
        QueryWrapper<WorkbenchQuickEntry> wrapper = activeTenantWrapper(entry.getTenantId());
        wrapper.eq("owner_type", entry.getOwnerType())
                .eq("owner_user_id", entry.getOwnerUserId())
                .eq("entry_code", entry.getEntryCode());
        if (StringUtils.hasText(entry.getId())) {
            wrapper.ne("id", entry.getId());
        }
        if (baseMapper.selectCount(wrapper) > 0) {
            throw new IllegalArgumentException("同一归属范围下快捷菜单编码不能重复");
        }
    }

    private WorkbenchQuickEntry requireEntry(String id, String tenantId, String ownerType, String ownerUserId) {
        requireText(id, "快捷菜单ID不能为空");
        QueryWrapper<WorkbenchQuickEntry> wrapper = activeTenantWrapper(tenantId);
        wrapper.eq("id", id)
                .eq("owner_type", ownerType)
                .eq("owner_user_id", ownerUserId);
        WorkbenchQuickEntry entry = baseMapper.selectOne(wrapper);
        if (entry == null) {
            throw new IllegalArgumentException("快捷菜单不存在");
        }
        return entry;
    }

    private List<WorkbenchQuickEntry> queryEnabledEntries(String tenantId, String ownerType, String ownerUserId) {
        QueryWrapper<WorkbenchQuickEntry> wrapper = activeTenantWrapper(tenantId);
        wrapper.eq("owner_type", ownerType)
                .eq("owner_user_id", ownerUserId)
                .eq("status", HomeWorkbenchConstants.STATUS_ENABLED)
                .orderByAsc("sort_no")
                .orderByAsc("create_time");
        return baseMapper.selectList(wrapper);
    }

    private WorkbenchQuickEntryVO toVO(WorkbenchQuickEntry entry) {
        WorkbenchQuickEntryVO vo = BeanUtil.toBean(entry, WorkbenchQuickEntryVO.class);
        vo.setConfig(parseConfig(entry.getConfigJson()));
        return vo;
    }
}
