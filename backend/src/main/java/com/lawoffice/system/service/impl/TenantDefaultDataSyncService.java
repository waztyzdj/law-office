package com.lawoffice.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawoffice.framework.config.TenantContextHolder;
import com.lawoffice.system.entity.SysCategory;
import com.lawoffice.system.entity.SysDict;
import com.lawoffice.system.entity.SysDictItem;
import com.lawoffice.system.entity.Tenant;
import com.lawoffice.system.mapper.SysCategoryMapper;
import com.lawoffice.system.mapper.SysDictItemMapper;
import com.lawoffice.system.mapper.SysDictMapper;
import com.lawoffice.system.mapper.TenantMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@Service
public class TenantDefaultDataSyncService {

    private static final String SYSTEM_TENANT_ID = "0";

    private final TenantMapper tenantMapper;
    private final SysDictMapper sysDictMapper;
    private final SysDictItemMapper sysDictItemMapper;
    private final SysCategoryMapper sysCategoryMapper;

    public TenantDefaultDataSyncService(
            TenantMapper tenantMapper,
            SysDictMapper sysDictMapper,
            SysDictItemMapper sysDictItemMapper,
            SysCategoryMapper sysCategoryMapper) {
        this.tenantMapper = tenantMapper;
        this.sysDictMapper = sysDictMapper;
        this.sysDictItemMapper = sysDictItemMapper;
        this.sysCategoryMapper = sysCategoryMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void syncDefaultDataToTenant(String tenantId, String operator) {
        if (!StringUtils.hasText(tenantId) || SYSTEM_TENANT_ID.equals(tenantId)) {
            return;
        }

        String resolvedOperator = resolveOperator(operator);
        syncDefaultDictionariesToTenant(tenantId, resolvedOperator);
        syncDefaultCategoriesToTenant(tenantId, resolvedOperator);
        log.info("Default dictionary and category data synced to tenant: {}", tenantId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void syncDefaultDictToAllTenants(String dictId, String operator) {
        if (!StringUtils.hasText(dictId)) {
            return;
        }

        SysDict sourceDict = runWithTenant(SYSTEM_TENANT_ID, () -> sysDictMapper.selectById(dictId));
        if (sourceDict == null || isDeleted(sourceDict.getDeleteFlag()) || !StringUtils.hasText(sourceDict.getDictCode())) {
            return;
        }

        String resolvedOperator = resolveOperator(operator);
        for (Tenant tenant : getEnabledBusinessTenants()) {
            ensureDictInTenant(sourceDict, tenant.getId(), resolvedOperator);
            syncDefaultDictItemsToTenant(sourceDict, tenant.getId(), resolvedOperator);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void syncDefaultDictItemToAllTenants(String dictItemId, String operator) {
        if (!StringUtils.hasText(dictItemId)) {
            return;
        }

        SysDictItem sourceItem = runWithTenant(SYSTEM_TENANT_ID, () -> sysDictItemMapper.selectById(dictItemId));
        if (sourceItem == null || isDeleted(sourceItem.getDeleteFlag()) || !StringUtils.hasText(sourceItem.getDictId())) {
            return;
        }

        SysDict sourceDict = runWithTenant(SYSTEM_TENANT_ID, () -> sysDictMapper.selectById(sourceItem.getDictId()));
        if (sourceDict == null || isDeleted(sourceDict.getDeleteFlag()) || !StringUtils.hasText(sourceDict.getDictCode())) {
            return;
        }

        String resolvedOperator = resolveOperator(operator);
        for (Tenant tenant : getEnabledBusinessTenants()) {
            SysDict targetDict = ensureDictInTenant(sourceDict, tenant.getId(), resolvedOperator);
            ensureDictItemInTenant(sourceItem, targetDict.getId(), tenant.getId(), resolvedOperator);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void syncDefaultCategoryToAllTenants(String categoryId, String operator) {
        if (!StringUtils.hasText(categoryId)) {
            return;
        }

        SysCategory sourceCategory = runWithTenant(SYSTEM_TENANT_ID, () -> sysCategoryMapper.selectById(categoryId));
        if (sourceCategory == null || isDeleted(sourceCategory.getDeleteFlag()) || !StringUtils.hasText(sourceCategory.getCode())) {
            return;
        }

        String resolvedOperator = resolveOperator(operator);
        for (Tenant tenant : getEnabledBusinessTenants()) {
            ensureCategoryInTenant(sourceCategory, tenant.getId(), resolvedOperator, new HashMap<>());
        }
    }

    public boolean isSystemTenantContext() {
        String tenantId = TenantContextHolder.getCurrentTenantId();
        return !StringUtils.hasText(tenantId) || SYSTEM_TENANT_ID.equals(tenantId);
    }

    private void syncDefaultDictionariesToTenant(String tenantId, String operator) {
        List<SysDict> sourceDicts = runWithTenant(SYSTEM_TENANT_ID, () -> sysDictMapper.selectList(
                new LambdaQueryWrapper<SysDict>()
                        .eq(SysDict::getDeleteFlag, 0)
                        .orderByAsc(SysDict::getId)
        ));

        for (SysDict sourceDict : sourceDicts) {
            if (!StringUtils.hasText(sourceDict.getDictCode())) {
                continue;
            }
            ensureDictInTenant(sourceDict, tenantId, operator);
            syncDefaultDictItemsToTenant(sourceDict, tenantId, operator);
        }
    }

    private void syncDefaultDictItemsToTenant(SysDict sourceDict, String tenantId, String operator) {
        SysDict targetDict = findTenantDictByCode(tenantId, sourceDict.getDictCode());
        if (targetDict == null || isDeleted(targetDict.getDeleteFlag())) {
            return;
        }

        List<SysDictItem> sourceItems = runWithTenant(SYSTEM_TENANT_ID, () -> sysDictItemMapper.selectList(
                new LambdaQueryWrapper<SysDictItem>()
                        .eq(SysDictItem::getDictId, sourceDict.getId())
                        .eq(SysDictItem::getDeleteFlag, 0)
                        .orderByAsc(SysDictItem::getSortOrder)
                        .orderByAsc(SysDictItem::getId)
        ));

        for (SysDictItem sourceItem : sourceItems) {
            ensureDictItemInTenant(sourceItem, targetDict.getId(), tenantId, operator);
        }
    }

    private SysDict ensureDictInTenant(SysDict sourceDict, String tenantId, String operator) {
        SysDict existingDict = findTenantDictByCode(tenantId, sourceDict.getDictCode());
        if (existingDict != null) {
            restoreDictIfDeleted(existingDict, sourceDict, operator);
            return existingDict;
        }

        SysDict targetDict = new SysDict();
        targetDict.setId(newId());
        targetDict.setTenantId(tenantId);
        targetDict.setDictName(sourceDict.getDictName());
        targetDict.setDictCode(sourceDict.getDictCode());
        targetDict.setDescription(sourceDict.getDescription());
        fillCreateFields(targetDict, operator);
        runWithTenant(tenantId, () -> {
            sysDictMapper.insert(targetDict);
            return null;
        });
        return targetDict;
    }

    private void ensureDictItemInTenant(SysDictItem sourceItem, String targetDictId, String tenantId, String operator) {
        if (!StringUtils.hasText(sourceItem.getItemValue())) {
            return;
        }

        SysDictItem existingItem = runWithTenant(tenantId, () -> sysDictItemMapper.selectOne(
                new LambdaQueryWrapper<SysDictItem>()
                        .eq(SysDictItem::getDictId, targetDictId)
                        .eq(SysDictItem::getItemValue, sourceItem.getItemValue())
                        .last("LIMIT 1")
        ));
        if (existingItem != null) {
            restoreDictItemIfDeleted(existingItem, sourceItem, operator);
            return;
        }

        SysDictItem targetItem = new SysDictItem();
        targetItem.setId(newId());
        targetItem.setTenantId(tenantId);
        targetItem.setDictId(targetDictId);
        targetItem.setItemText(sourceItem.getItemText());
        targetItem.setItemValue(sourceItem.getItemValue());
        targetItem.setDescription(sourceItem.getDescription());
        targetItem.setSortOrder(sourceItem.getSortOrder());
        targetItem.setStatus(sourceItem.getStatus());
        fillCreateFields(targetItem, operator);
        runWithTenant(tenantId, () -> {
            sysDictItemMapper.insert(targetItem);
            return null;
        });
    }

    private void syncDefaultCategoriesToTenant(String tenantId, String operator) {
        List<SysCategory> sourceCategories = runWithTenant(SYSTEM_TENANT_ID, () -> sysCategoryMapper.selectList(
                new LambdaQueryWrapper<SysCategory>()
                        .eq(SysCategory::getDeleteFlag, 0)
                        .orderByAsc(SysCategory::getId)
        ));

        Map<String, String> sourceToTargetIdMap = new HashMap<>();
        for (SysCategory sourceCategory : sourceCategories) {
            if (StringUtils.hasText(sourceCategory.getCode())) {
                ensureCategoryInTenant(sourceCategory, tenantId, operator, sourceToTargetIdMap);
            }
        }
    }

    private SysCategory ensureCategoryInTenant(
            SysCategory sourceCategory,
            String tenantId,
            String operator,
            Map<String, String> sourceToTargetIdMap) {
        if (sourceToTargetIdMap.containsKey(sourceCategory.getId())) {
            return runWithTenant(tenantId, () -> sysCategoryMapper.selectById(sourceToTargetIdMap.get(sourceCategory.getId())));
        }

        SysCategory existingCategory = findTenantCategoryByCode(tenantId, sourceCategory.getCode());
        if (existingCategory != null) {
            restoreCategoryIfDeleted(existingCategory, sourceCategory, operator);
            sourceToTargetIdMap.put(sourceCategory.getId(), existingCategory.getId());
            return existingCategory;
        }

        String targetPid = resolveTargetCategoryPid(sourceCategory, tenantId, operator, sourceToTargetIdMap);

        SysCategory targetCategory = new SysCategory();
        targetCategory.setId(newId());
        targetCategory.setTenantId(tenantId);
        targetCategory.setPid(targetPid);
        targetCategory.setName(sourceCategory.getName());
        targetCategory.setCode(sourceCategory.getCode());
        targetCategory.setHasChild(sourceCategory.getHasChild());
        fillCreateFields(targetCategory, operator);
        runWithTenant(tenantId, () -> {
            sysCategoryMapper.insert(targetCategory);
            return null;
        });
        sourceToTargetIdMap.put(sourceCategory.getId(), targetCategory.getId());
        return targetCategory;
    }

    private String resolveTargetCategoryPid(
            SysCategory sourceCategory,
            String tenantId,
            String operator,
            Map<String, String> sourceToTargetIdMap) {
        if (!StringUtils.hasText(sourceCategory.getPid())) {
            return null;
        }
        if (sourceToTargetIdMap.containsKey(sourceCategory.getPid())) {
            return sourceToTargetIdMap.get(sourceCategory.getPid());
        }

        SysCategory sourceParent = runWithTenant(SYSTEM_TENANT_ID, () -> sysCategoryMapper.selectById(sourceCategory.getPid()));
        if (sourceParent == null || isDeleted(sourceParent.getDeleteFlag()) || !StringUtils.hasText(sourceParent.getCode())) {
            return null;
        }

        SysCategory targetParent = ensureCategoryInTenant(sourceParent, tenantId, operator, sourceToTargetIdMap);
        return targetParent != null ? targetParent.getId() : null;
    }

    private SysDict findTenantDictByCode(String tenantId, String dictCode) {
        return runWithTenant(tenantId, () -> sysDictMapper.selectOne(
                new LambdaQueryWrapper<SysDict>()
                        .eq(SysDict::getDictCode, dictCode)
                        .last("LIMIT 1")
        ));
    }

    private SysCategory findTenantCategoryByCode(String tenantId, String code) {
        return runWithTenant(tenantId, () -> sysCategoryMapper.selectOne(
                new LambdaQueryWrapper<SysCategory>()
                        .eq(SysCategory::getCode, code)
                        .last("LIMIT 1")
        ));
    }

    private void restoreDictIfDeleted(SysDict targetDict, SysDict sourceDict, String operator) {
        if (!isDeleted(targetDict.getDeleteFlag())) {
            return;
        }
        targetDict.setDictName(sourceDict.getDictName());
        targetDict.setDescription(sourceDict.getDescription());
        fillRestoreFields(targetDict, operator);
        runWithTenant(targetDict.getTenantId(), () -> {
            sysDictMapper.updateById(targetDict);
            return null;
        });
    }

    private void restoreDictItemIfDeleted(SysDictItem targetItem, SysDictItem sourceItem, String operator) {
        if (!isDeleted(targetItem.getDeleteFlag())) {
            return;
        }
        targetItem.setItemText(sourceItem.getItemText());
        targetItem.setDescription(sourceItem.getDescription());
        targetItem.setSortOrder(sourceItem.getSortOrder());
        targetItem.setStatus(sourceItem.getStatus());
        fillRestoreFields(targetItem, operator);
        runWithTenant(targetItem.getTenantId(), () -> {
            sysDictItemMapper.updateById(targetItem);
            return null;
        });
    }

    private void restoreCategoryIfDeleted(SysCategory targetCategory, SysCategory sourceCategory, String operator) {
        if (!isDeleted(targetCategory.getDeleteFlag())) {
            return;
        }
        targetCategory.setName(sourceCategory.getName());
        targetCategory.setHasChild(sourceCategory.getHasChild());
        fillRestoreFields(targetCategory, operator);
        runWithTenant(targetCategory.getTenantId(), () -> {
            sysCategoryMapper.updateById(targetCategory);
            return null;
        });
    }

    private List<Tenant> getEnabledBusinessTenants() {
        return tenantMapper.selectList(
                new LambdaQueryWrapper<Tenant>()
                        .eq(Tenant::getDeleteFlag, 0)
                        .eq(Tenant::getStatus, 1)
                        .ne(Tenant::getId, SYSTEM_TENANT_ID)
                        .orderByAsc(Tenant::getId)
        );
    }

    private void fillCreateFields(com.lawoffice.framework.entity.BaseEntity entity, String operator) {
        entity.setCreateBy(operator);
        entity.setCreateTime(LocalDateTime.now());
        entity.setDeleteFlag(0);
    }

    private void fillRestoreFields(com.lawoffice.framework.entity.BaseEntity entity, String operator) {
        entity.setDeleteFlag(0);
        entity.setDeleteTime(null);
        entity.setDeleteBy(null);
        entity.setUpdateBy(operator);
        entity.setUpdateTime(LocalDateTime.now());
    }

    private boolean isDeleted(Integer deleteFlag) {
        return deleteFlag != null && deleteFlag == 1;
    }

    private String resolveOperator(String operator) {
        return StringUtils.hasText(operator) ? operator : "system";
    }

    private String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private <T> T runWithTenant(String tenantId, Supplier<T> supplier) {
        String previousTenantId = TenantContextHolder.getCurrentTenantId();
        if (StringUtils.hasText(tenantId)) {
            TenantContextHolder.setCurrentTenantId(tenantId);
        }
        try {
            return supplier.get();
        } finally {
            if (StringUtils.hasText(previousTenantId)) {
                TenantContextHolder.setCurrentTenantId(previousTenantId);
            } else {
                TenantContextHolder.clear();
            }
        }
    }
}
