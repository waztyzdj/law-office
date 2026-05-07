package com.lawoffice.system.service.impl;

import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.system.entity.DepartPermission;
import com.lawoffice.system.mapper.DepartPermissionMapper;
import com.lawoffice.system.service.IDepartPermissionService;
import org.springframework.stereotype.Service;

@Service
public class DepartPermissionServiceImpl extends BaseServiceImpl<DepartPermissionMapper, DepartPermission> implements IDepartPermissionService {
}
