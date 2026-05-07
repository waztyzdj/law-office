package com.lawoffice.system.service.impl;

import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.system.entity.DepartRoleUser;
import com.lawoffice.system.mapper.DepartRoleUserMapper;
import com.lawoffice.system.service.IDepartRoleUserService;
import org.springframework.stereotype.Service;

@Service
public class DepartRoleUserServiceImpl extends BaseServiceImpl<DepartRoleUserMapper, DepartRoleUser> implements IDepartRoleUserService {
}
