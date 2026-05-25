package com.lawoffice.system.service.impl;

import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.system.entity.SysDictItem;
import com.lawoffice.system.mapper.SysDictItemMapper;
import com.lawoffice.system.service.ISysDictItemService;
import com.lawoffice.system.vo.SysDictItemVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SysDictItemServiceImpl extends BaseServiceImpl<SysDictItemMapper, SysDictItem, SysDictItemVO> implements ISysDictItemService {

    @Resource
    private TenantDefaultDataSyncService tenantDefaultDataSyncService;

    @Override
    protected void doAfterSave(BaseDTO<SysDictItem> saveDTO, SysDictItemVO vo) {
        if (vo == null || !StringUtils.hasText(vo.getId()) || !tenantDefaultDataSyncService.isSystemTenantContext()) {
            return;
        }

        String operator = saveDTO != null && saveDTO.getContext() != null ? saveDTO.getContext().getUsername() : null;
        tenantDefaultDataSyncService.syncDefaultDictItemToAllTenants(vo.getId(), operator);
    }
}
