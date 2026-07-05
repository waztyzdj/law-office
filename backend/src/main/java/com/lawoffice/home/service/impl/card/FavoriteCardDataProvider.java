package com.lawoffice.home.service.impl.card;

import static com.lawoffice.system.constant.SysFileConstants.FLAG_YES;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lawoffice.document.dto.DocumentAccessContext;
import com.lawoffice.document.service.IDocumentAccessContextService;
import com.lawoffice.document.service.IDocumentFileViewService;
import com.lawoffice.document.vo.DocumentFileVO;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.home.constant.HomeWorkbenchConstants;
import com.lawoffice.home.entity.WorkbenchCard;
import com.lawoffice.home.req.WorkbenchCardDataReq;
import com.lawoffice.home.service.IWorkbenchCardDataProvider;
import com.lawoffice.home.vo.WorkbenchCardDataVO;
import com.lawoffice.system.entity.SysFiles;
import com.lawoffice.system.mapper.SysFilesMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class FavoriteCardDataProvider extends AbstractWorkbenchCardDataProvider implements IWorkbenchCardDataProvider {

    private final SysFilesMapper sysFilesMapper;
    private final IDocumentAccessContextService documentAccessContextService;
    private final IDocumentFileViewService documentFileViewService;

    public FavoriteCardDataProvider(
            SysFilesMapper sysFilesMapper,
            IDocumentAccessContextService documentAccessContextService,
            IDocumentFileViewService documentFileViewService) {
        this.sysFilesMapper = sysFilesMapper;
        this.documentAccessContextService = documentAccessContextService;
        this.documentFileViewService = documentFileViewService;
    }

    @Override
    public boolean supports(String cardCode) {
        return HomeWorkbenchConstants.CARD_FAVORITE.equals(cardCode);
    }

    @Override
    public WorkbenchCardDataVO loadData(WorkbenchCardDataReq req, WorkbenchCard card, RequestContext context) {
        PageVO<DocumentFileVO> page = loadFavoritePage(context);
        WorkbenchCardDataVO vo = emptyData(card);
        vo.getSummary().put("favoriteTotal", total(page));
        vo.setItems(mapFavoriteItems(page));
        return vo;
    }

    private PageVO<DocumentFileVO> loadFavoritePage(RequestContext context) {
        String tenantId = requireTenantId(context);
        DocumentAccessContext accessContext = documentAccessContextService.buildDocumentAccessContext(
                context.getUsername(),
                tenantId);
        List<SysFiles> starredItems = sysFilesMapper.selectList(Wrappers.lambdaQuery(SysFiles.class)
                .eq(SysFiles::getTenantId, tenantId)
                .eq(SysFiles::getCreateBy, context.getUsername())
                .eq(SysFiles::getIzStar, FLAG_YES)
                .eq(SysFiles::getDeleteFlag, 0)
                .orderByDesc(SysFiles::getStarTime)
                .orderByDesc(SysFiles::getUpdateTime)
                .orderByDesc(SysFiles::getCreateTime)
                .orderByAsc(SysFiles::getFileName));
        List<FavoriteFileEntry> favoriteFiles = resolveFavoriteFiles(starredItems, tenantId, context.getUsername());
        List<FavoriteFileEntry> limitedFiles = favoriteFiles.stream()
                .limit(resolveListFetchLimit())
                .toList();
        List<DocumentFileVO> records = documentFileViewService.buildDocumentVOList(
                limitedFiles.stream().map(FavoriteFileEntry::file).toList(),
                accessContext);
        Map<String, LocalDateTime> favoriteTimeById = new LinkedHashMap<>();
        limitedFiles.forEach(entry -> favoriteTimeById.put(entry.file().getId(), entry.favoriteTime()));
        records.forEach(record -> {
            LocalDateTime favoriteTime = favoriteTimeById.get(record.getId());
            if (favoriteTime != null && record.getStarTime() == null) {
                record.setStarTime(favoriteTime);
            }
        });
        return new PageVO<>(records, favoriteFiles.size(), 1, resolveListFetchLimit());
    }

    /**
     * 收藏文件夹在工作台中不作为列表项展示，而是继承文件夹收藏时间铺开其子文件。
     */
    private List<FavoriteFileEntry> resolveFavoriteFiles(List<SysFiles> starredItems, String tenantId, String username) {
        Map<String, FavoriteFileEntry> entries = new LinkedHashMap<>();
        Map<String, LocalDateTime> favoriteFolderTimes = new LinkedHashMap<>();
        for (SysFiles item : starredItems) {
            if (!StringUtils.hasText(item.getId())) {
                continue;
            }
            LocalDateTime favoriteTime = favoriteTime(item);
            if (FLAG_YES.equals(item.getIzFolder())) {
                favoriteFolderTimes.put(item.getId(), favoriteTime);
                continue;
            }
            putFavoriteFile(entries, item, favoriteTime, true);
        }
        collectFavoriteFolderFiles(entries, favoriteFolderTimes, tenantId, username);
        return entries.values().stream()
                .sorted(this::compareFavoriteEntry)
                .toList();
    }

    /**
     * 按层展开用户收藏的文件夹，并保留父级收藏时间用于最终倒序排序。
     */
    private void collectFavoriteFolderFiles(
            Map<String, FavoriteFileEntry> entries,
            Map<String, LocalDateTime> favoriteFolderTimes,
            String tenantId,
            String username) {
        Map<String, LocalDateTime> cursor = new LinkedHashMap<>(favoriteFolderTimes);
        Set<String> visitedFolderIds = new HashSet<>(favoriteFolderTimes.keySet());
        int guard = 0;
        while (!cursor.isEmpty() && guard++ < 20) {
            List<SysFiles> children = sysFilesMapper.selectList(Wrappers.lambdaQuery(SysFiles.class)
                    .eq(SysFiles::getTenantId, tenantId)
                    .eq(SysFiles::getCreateBy, username)
                    .eq(SysFiles::getDeleteFlag, 0)
                    .in(SysFiles::getParentId, cursor.keySet())
                    .orderByAsc(SysFiles::getFileName));
            Map<String, LocalDateTime> nextCursor = new LinkedHashMap<>();
            for (SysFiles child : children) {
                LocalDateTime inheritedFavoriteTime = cursor.get(child.getParentId());
                if (FLAG_YES.equals(child.getIzFolder())) {
                    if (StringUtils.hasText(child.getId()) && visitedFolderIds.add(child.getId())) {
                        nextCursor.put(child.getId(), inheritedFavoriteTime);
                    }
                    continue;
                }
                putFavoriteFile(entries, child, inheritedFavoriteTime, false);
            }
            cursor = nextCursor;
        }
    }

    /**
     * 同一文件既可能被直接收藏，也可能来自收藏文件夹；直接收藏优先，间接收藏取较新的继承时间。
     */
    private void putFavoriteFile(
            Map<String, FavoriteFileEntry> entries,
            SysFiles file,
            LocalDateTime favoriteTime,
            boolean directFavorite) {
        if (!StringUtils.hasText(file.getId())) {
            return;
        }
        FavoriteFileEntry existing = entries.get(file.getId());
        if (existing == null
                || (directFavorite && !existing.directFavorite())
                || (!existing.directFavorite() && isLater(favoriteTime, existing.favoriteTime()))) {
            entries.put(file.getId(), new FavoriteFileEntry(file, favoriteTime, directFavorite));
        }
    }

    private int compareFavoriteEntry(FavoriteFileEntry left, FavoriteFileEntry right) {
        int timeCompare = compareNullableTimeDesc(left.favoriteTime(), right.favoriteTime());
        if (timeCompare != 0) {
            return timeCompare;
        }
        return String.valueOf(left.file().getFileName()).compareToIgnoreCase(String.valueOf(right.file().getFileName()));
    }

    private int compareNullableTimeDesc(LocalDateTime left, LocalDateTime right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return right.compareTo(left);
    }

    private boolean isLater(LocalDateTime candidate, LocalDateTime current) {
        if (candidate == null) {
            return false;
        }
        return current == null || candidate.isAfter(current);
    }

    private List<Map<String, Object>> mapFavoriteItems(PageVO<DocumentFileVO> page) {
        if (page == null || page.getRecords() == null) {
            return List.of();
        }
        return page.getRecords().stream()
                .sorted(Comparator
                        .comparing((DocumentFileVO file) -> favoriteTime(file),
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(file -> file.getFileName() == null ? "" : file.getFileName(),
                                String.CASE_INSENSITIVE_ORDER))
                .map(file -> {
                    Map<String, Object> item = item(
                            file.getId(),
                            file.getFileName(),
                            "favorite",
                            file.getFileType(),
                            favoriteTime(file),
                            HomeWorkbenchConstants.TARGET_TYPE_ROUTE,
                            "/document-center?scope=starred",
                            file.getId());
                    item.put("fileType", file.getFileType());
                    item.put("izFolder", file.getIzFolder());
                    item.put("storeType", file.getStoreType());
                    item.put("fileName", file.getFileName());
                    item.put("fileSize", file.getFileSize());
                    item.put("starTime", file.getStarTime());
                    item.put("izStar", file.getIzStar());
                    item.put("canDownload", file.getCanDownload());
                    item.put("canManage", file.getCanManage());
                    item.put("canUpdate", file.getCanUpdate());
                    item.put("createTime", file.getCreateTime());
                    item.put("updateTime", file.getUpdateTime());
                    return item;
                })
                .toList();
    }

    private String requireTenantId(RequestContext context) {
        if (context == null || !StringUtils.hasText(context.getTenantId())) {
            throw new IllegalArgumentException("缺少租户上下文");
        }
        return context.getTenantId();
    }

    private LocalDateTime favoriteTime(DocumentFileVO file) {
        if (file == null) {
            return null;
        }
        if (file.getStarTime() != null) {
            return file.getStarTime();
        }
        return file.getUpdateTime() != null ? file.getUpdateTime() : file.getCreateTime();
    }

    private LocalDateTime favoriteTime(SysFiles file) {
        if (file == null) {
            return null;
        }
        if (file.getStarTime() != null) {
            return file.getStarTime();
        }
        return file.getUpdateTime() != null ? file.getUpdateTime() : file.getCreateTime();
    }

    private record FavoriteFileEntry(SysFiles file, LocalDateTime favoriteTime, boolean directFavorite) {
    }
}
