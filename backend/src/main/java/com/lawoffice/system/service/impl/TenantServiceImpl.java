package com.lawoffice.system.service.impl;

import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.system.entity.Tenant;
import com.lawoffice.system.mapper.TenantMapper;
import com.lawoffice.system.service.ITenantService;
import org.springframework.stereotype.Service;

@Service
public class TenantServiceImpl extends BaseServiceImpl<TenantMapper, Tenant> implements ITenantService {
}
