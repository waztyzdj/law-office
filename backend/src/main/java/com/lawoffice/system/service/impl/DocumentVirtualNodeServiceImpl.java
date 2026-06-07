package com.lawoffice.system.service.impl;

import static com.lawoffice.system.constant.DocumentCenterConstants.*;

import com.lawoffice.system.dto.BusinessDocumentAccessContext;
import com.lawoffice.system.dto.DocumentBusinessRecordNode;
import com.lawoffice.system.service.IBusinessDocumentProvider;
import com.lawoffice.system.service.IDocumentVirtualNodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DocumentVirtualNodeServiceImpl implements IDocumentVirtualNodeService {

    private final List<IBusinessDocumentProvider> businessDocumentProviders;

    @Override
    public String sharedOwnerId(String owner) {
        return SHARED_OWNER_PREFIX + owner;
    }

    @Override
    public boolean isSharedOwnerVirtualId(String id) {
        return StringUtils.hasText(id) && id.startsWith(SHARED_OWNER_PREFIX);
    }

    @Override
    public String parseSharedOwner(String id) {
        if (!isSharedOwnerVirtualId(id)) {
            return null;
        }
        return id.substring(SHARED_OWNER_PREFIX.length());
    }

    @Override
    public String businessModuleId(String bizType) {
        return BUSINESS_MODULE_PREFIX + bizType;
    }

    @Override
    public String businessRecordId(String bizType, String bizId) {
        return BUSINESS_RECORD_PREFIX + bizType + ":" + bizId;
    }

    @Override
    public boolean isBusinessModuleVirtualId(String id) {
        return StringUtils.hasText(id) && id.startsWith(BUSINESS_MODULE_PREFIX);
    }

    @Override
    public boolean isBusinessRecordVirtualId(String id) {
        return StringUtils.hasText(id) && id.startsWith(BUSINESS_RECORD_PREFIX);
    }

    @Override
    public String parseBusinessModuleBizType(String id) {
        if (!isBusinessModuleVirtualId(id)) {
            return null;
        }
        return id.substring(BUSINESS_MODULE_PREFIX.length());
    }

    @Override
    public DocumentBusinessRecordNode parseBusinessRecordNode(String id) {
        if (!isBusinessRecordVirtualId(id)) {
            return null;
        }
        String value = id.substring(BUSINESS_RECORD_PREFIX.length());
        String[] parts = value.split(":", 2);
        if (parts.length != 2) {
            return null;
        }
        return new DocumentBusinessRecordNode(parts[0], parts[1]);
    }

    @Override
    public String resolveBusinessModuleName(String bizType) {
        IBusinessDocumentProvider provider = findBusinessDocumentProvider(bizType);
        return provider == null ? bizType : provider.moduleName();
    }

    @Override
    public Map<String, String> resolveBusinessRecordNames(
            String bizType,
            Collection<String> bizIds,
            BusinessDocumentAccessContext context) {
        if (bizIds.isEmpty()) {
            return Collections.emptyMap();
        }
        IBusinessDocumentProvider provider = findBusinessDocumentProvider(bizType);
        if (provider != null) {
            Map<String, String> recordNames = provider.resolveRecordNames(bizIds, context);
            if (recordNames != null && !recordNames.isEmpty()) {
                return recordNames;
            }
        }
        return bizIds.stream()
                .collect(Collectors.toMap(
                        bizId -> bizId,
                        bizId -> bizType + "-" + bizId,
                        (left, right) -> left));
    }

    @Override
    public IBusinessDocumentProvider findBusinessDocumentProvider(String bizType) {
        if (!StringUtils.hasText(bizType)) {
            return null;
        }
        return businessDocumentProviders.stream()
                .filter(provider -> Objects.equals(provider.bizType(), bizType))
                .findFirst()
                .orElse(null);
    }
}
