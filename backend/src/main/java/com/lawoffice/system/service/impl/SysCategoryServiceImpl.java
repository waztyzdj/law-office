package com.lawoffice.system.service.impl;

import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.system.entity.SysCategory;
import com.lawoffice.system.mapper.SysCategoryMapper;
import com.lawoffice.system.service.ISysCategoryService;
import com.lawoffice.system.service.ITenantDefaultDataSyncService;
import com.lawoffice.system.vo.SysCategoryVO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SysCategoryServiceImpl extends BaseServiceImpl<SysCategoryMapper, SysCategory, SysCategoryVO> implements ISysCategoryService {

    @Resource
    private ITenantDefaultDataSyncService tenantDefaultDataSyncService;

    @Override
    protected void doAfterSave(BaseDTO<SysCategory> saveDTO, SysCategoryVO vo) {
        if (vo == null || !StringUtils.hasText(vo.getId()) || !tenantDefaultDataSyncService.isSystemTenantContext()) {
            return;
        }

        String operator = saveDTO != null && saveDTO.getContext() != null ? saveDTO.getContext().getUsername() : null;
        tenantDefaultDataSyncService.syncDefaultCategoryToAllTenants(vo.getId(), operator);
    }
}
