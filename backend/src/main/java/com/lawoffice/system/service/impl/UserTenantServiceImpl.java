package com.lawoffice.system.service.impl;

import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.system.entity.UserTenant;
import com.lawoffice.system.mapper.UserTenantMapper;
import com.lawoffice.system.service.IUserTenantService;
import org.springframework.stereotype.Service;

@Service
public class UserTenantServiceImpl extends BaseServiceImpl<UserTenantMapper, UserTenant> implements IUserTenantService {
}
