package com.lawoffice.document.service.impl;

import static com.lawoffice.document.constant.DocumentCenterConstants.*;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lawoffice.document.dto.BusinessDocumentAccessContext;
import com.lawoffice.document.dto.DocumentAccessContext;
import com.lawoffice.document.dto.DocumentBusinessRecordNode;
import com.lawoffice.system.entity.SysFileRelation;
import com.lawoffice.system.entity.SysFiles;
import com.lawoffice.system.mapper.SysFileRelationMapper;
import com.lawoffice.system.mapper.SysFilesMapper;
import com.lawoffice.document.service.IBusinessDocumentProvider;
import com.lawoffice.document.service.IDocumentBusinessAccessService;
import com.lawoffice.document.service.IDocumentVirtualNodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentBusinessAccessServiceImpl implements IDocumentBusinessAccessService {

    private final SysFilesMapper sysFilesMapper;
    private final SysFileRelationMapper fileRelationMapper;
    private final IDocumentVirtualNodeService documentVirtualNodeService;

    @Override
    public List<String> findActiveBusinessFileIds(DocumentAccessContext context) {
        return findActiveBusinessRelations(context)
                .stream()
                .map(SysFileRelation::getFileId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    @Override
    public List<SysFileRelation> findActiveBusinessRelations(DocumentAccessContext context) {
        if (context.cache().getActiveBusinessRelations() != null) {
            return context.cache().getActiveBusinessRelations();
        }
        context.cache().setActiveBusinessRelations(fileRelationMapper.selectList(Wrappers.lambdaQuery(SysFileRelation.class)
                        .select(
                                SysFileRelation::getFileId,
                                SysFileRelation::getBizType,
                                SysFileRelation::getBizId,
                                SysFileRelation::getRelationType)
                        .eq(SysFileRelation::getTenantId, context.tenantId())
                        .eq(SysFileRelation::getDeleteFlag, 0))
                .stream()
                .filter(this::isBusinessRelation)
                .toList());
        return context.cache().getActiveBusinessRelations();
    }

    @Override
    public List<String> findAccessibleBusinessFileIds(DocumentAccessContext context) {
        return findAccessibleBusinessRelations(context).stream()
                .map(SysFileRelation::getFileId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    @Override
    public List<SysFileRelation> findAccessibleBusinessRelations(DocumentAccessContext context) {
        if (context.cache().getAccessibleBusinessRelations() != null) {
            return context.cache().getAccessibleBusinessRelations();
        }
        List<SysFileRelation> relations = findActiveBusinessRelations(context);
        if (relations.isEmpty()) {
            context.cache().setAccessibleBusinessRelations(Collections.emptyList());
            return context.cache().getAccessibleBusinessRelations();
        }
        List<String> fileIds = relations.stream()
                .map(SysFileRelation::getFileId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        Map<String, SysFiles> fileMap = selectActiveFilesByIds(context, fileIds).stream()
                .collect(Collectors.toMap(SysFiles::getId, file -> file, (left, right) -> left));
        Map<String, Set<String>> accessibleBizIds = resolveAccessibleBusinessBizIds(relations, fileMap, context);
        context.cache().setAccessibleBusinessRelations(relations.stream()
                .filter(relation -> fileMap.containsKey(relation.getFileId()))
                .filter(relation -> accessibleBizIds.getOrDefault(relation.getBizType(), Collections.emptySet())
                        .contains(relation.getBizId()))
                .toList());
        return context.cache().getAccessibleBusinessRelations();
    }

    @Override
    public boolean hasAccessibleBusinessRecord(DocumentBusinessRecordNode recordNode, DocumentAccessContext context) {
        if (recordNode == null
                || !StringUtils.hasText(recordNode.bizType())
                || !StringUtils.hasText(recordNode.bizId())) {
            return false;
        }
        return findAccessibleBusinessRelations(context).stream()
                .anyMatch(relation -> Objects.equals(relation.getBizType(), recordNode.bizType())
                        && Objects.equals(relation.getBizId(), recordNode.bizId()));
    }

    @Override
    public boolean hasAccessibleBusinessRecordFile(
            String fileId,
            DocumentBusinessRecordNode recordNode,
            DocumentAccessContext context) {
        if (!StringUtils.hasText(fileId) || recordNode == null) {
            return false;
        }
        return findAccessibleBusinessRelations(context).stream()
                .anyMatch(relation -> Objects.equals(relation.getFileId(), fileId)
                        && Objects.equals(relation.getBizType(), recordNode.bizType())
                        && Objects.equals(relation.getBizId(), recordNode.bizId()));
    }

    @Override
    public boolean hasActiveBusinessRelation(String fileId, String tenantId) {
        if (!StringUtils.hasText(fileId)) {
            return false;
        }
        return fileRelationMapper.selectList(Wrappers.lambdaQuery(SysFileRelation.class)
                        .eq(SysFileRelation::getTenantId, tenantId)
                        .eq(SysFileRelation::getFileId, fileId)
                        .eq(SysFileRelation::getDeleteFlag, 0))
                .stream()
                .anyMatch(this::isBusinessRelation);
    }

    @Override
    public boolean hasBusinessDocumentAccess(SysFiles file, DocumentAccessContext context) {
        if (file == null) {
            return false;
        }
        List<SysFileRelation> relations = fileRelationMapper.selectList(Wrappers.lambdaQuery(SysFileRelation.class)
                        .eq(SysFileRelation::getTenantId, context.tenantId())
                        .eq(SysFileRelation::getFileId, file.getId())
                        .eq(SysFileRelation::getDeleteFlag, 0))
                .stream()
                .filter(this::isBusinessRelation)
                .toList();
        if (relations.isEmpty()) {
            return false;
        }
        return relations.stream()
                .anyMatch(relation -> hasBusinessRelationAccess(relation, context));
    }

    /**
     * 关系表被文档中心复用了两类个人整理关系，因此这里按前缀和关系类型双保险排除。
     */
    @Override
    public boolean isBusinessRelation(SysFileRelation relation) {
        if (relation == null) {
            return false;
        }
        String bizType = relation.getBizType();
        if (!StringUtils.hasText(bizType)
                || BUSINESS_DOCUMENT_EXCLUDED_BIZ_TYPES.contains(bizType)
                || DEPART_SHARED_RELATION_BIZ_TYPE.equals(bizType)
                || bizType.startsWith(PERSONAL_SHARED_RELATION_PREFIX)
                || bizType.startsWith(PERSONAL_BUSINESS_RELATION_PREFIX)) {
            return false;
        }
        Integer relationType = relation.getRelationType();
        return !Objects.equals(relationType, PERSONAL_SHARED_RELATION_TYPE)
                && !Objects.equals(relationType, PERSONAL_BUSINESS_RELATION_TYPE)
                && !Objects.equals(relationType, DEPART_SHARED_RELATION_TYPE);
    }

    @Override
    public BusinessDocumentAccessContext toBusinessDocumentAccessContext(DocumentAccessContext context) {
        return new BusinessDocumentAccessContext(
                context.username(),
                context.userId(),
                context.tenantId(),
                context.departIds(),
                context.roleIds());
    }

    private Map<String, Set<String>> resolveAccessibleBusinessBizIds(
            List<SysFileRelation> relations,
            Map<String, SysFiles> fileMap,
            DocumentAccessContext context) {
        Map<String, Set<String>> bizIdsByType = new LinkedHashMap<>();
        for (SysFileRelation relation : relations) {
            if (relation == null
                    || !StringUtils.hasText(relation.getBizType())
                    || !StringUtils.hasText(relation.getBizId())
                    || !fileMap.containsKey(relation.getFileId())) {
                continue;
            }
            bizIdsByType.computeIfAbsent(relation.getBizType(), key -> new LinkedHashSet<>())
                    .add(relation.getBizId());
        }
        Map<String, Set<String>> accessibleBizIds = new LinkedHashMap<>();
        BusinessDocumentAccessContext accessContext = toBusinessDocumentAccessContext(context);
        for (Map.Entry<String, Set<String>> entry : bizIdsByType.entrySet()) {
            IBusinessDocumentProvider provider = documentVirtualNodeService.findBusinessDocumentProvider(entry.getKey());
            if (provider == null) {
                accessibleBizIds.put(entry.getKey(), Collections.emptySet());
                continue;
            }
            Set<String> ids = provider.filterAccessibleBizIds(entry.getValue(), accessContext);
            accessibleBizIds.put(entry.getKey(), ids == null ? Collections.emptySet() : ids);
        }
        return accessibleBizIds;
    }

    @Override
    public boolean hasBusinessRelationAccess(SysFileRelation relation, DocumentAccessContext context) {
        if (relation == null) {
            return false;
        }
        IBusinessDocumentProvider provider = documentVirtualNodeService.findBusinessDocumentProvider(relation.getBizType());
        return provider != null && provider.canAccess(relation.getBizId(), toBusinessDocumentAccessContext(context));
    }

    private List<SysFiles> selectActiveFilesByIds(DocumentAccessContext context, List<String> fileIds) {
        if (fileIds.isEmpty()) {
            return Collections.emptyList();
        }
        return sysFilesMapper.selectList(Wrappers.lambdaQuery(SysFiles.class)
                .eq(SysFiles::getTenantId, context.tenantId())
                .eq(SysFiles::getDeleteFlag, 0)
                .in(SysFiles::getId, fileIds));
    }
}
