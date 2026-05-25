package com.lawoffice.system.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.system.entity.SysDict;
import com.lawoffice.system.entity.SysDictItem;
import com.lawoffice.system.mapper.SysDictMapper;
import com.lawoffice.system.mapper.SysDictItemMapper;
import com.lawoffice.system.service.ISysDictService;
import com.lawoffice.system.service.ITenantDefaultDataSyncService;
import com.lawoffice.system.vo.DictOptionVO;
import com.lawoffice.system.vo.SysDictVO;
import com.lawoffice.framework.result.BaseResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.util.List;

@Service
public class SysDictServiceImpl extends BaseServiceImpl<SysDictMapper, SysDict, SysDictVO> implements ISysDictService {

    @Resource
    private SysDictItemMapper sysDictItemMapper;

    @Resource
    private ITenantDefaultDataSyncService tenantDefaultDataSyncService;

    @Override
    protected void doAfterSave(BaseDTO<SysDict> saveDTO, SysDictVO vo) {
        if (vo == null || !StringUtils.hasText(vo.getId()) || !tenantDefaultDataSyncService.isSystemTenantContext()) {
            return;
        }

        String operator = saveDTO != null && saveDTO.getContext() != null ? saveDTO.getContext().getUsername() : null;
        tenantDefaultDataSyncService.syncDefaultDictToAllTenants(vo.getId(), operator);
    }

    @Override
    public BaseResult<List<DictOptionVO>> listOptionsByCode(String dictCode) {
        if (!StringUtils.hasText(dictCode)) {
            return BaseResult.success(List.of());
        }

        SysDict dict = lambdaQuery()
                .eq(SysDict::getDictCode, dictCode)
                .eq(SysDict::getDeleteFlag, 0)
                .one();

        if (dict == null || !StringUtils.hasText(dict.getId())) {
            return BaseResult.success(List.of());
        }

        List<SysDictItem> items = sysDictItemMapper.selectList(
                Wrappers.<SysDictItem>lambdaQuery()
                        .eq(SysDictItem::getDictId, dict.getId())
                        .eq(SysDictItem::getDeleteFlag, 0)
                        .eq(SysDictItem::getStatus, 1)
                        .orderByAsc(SysDictItem::getSortOrder)
                        .orderByAsc(SysDictItem::getId)
        );

        if (CollUtil.isEmpty(items)) {
            return BaseResult.success(List.of());
        }

        List<DictOptionVO> options = items.stream()
                .map(item -> new DictOptionVO(item.getItemText(), item.getItemValue()))
                .toList();
        return BaseResult.success(options);
    }
}
