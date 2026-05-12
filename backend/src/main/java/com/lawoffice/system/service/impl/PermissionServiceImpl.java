package com.lawoffice.system.service.impl;

import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.system.entity.Permission;
import com.lawoffice.system.mapper.PermissionMapper;
import com.lawoffice.system.service.IPermissionService;
import com.lawoffice.system.vo.PermissionVO;
import org.springframework.stereotype.Service;

@Service
public class PermissionServiceImpl extends BaseServiceImpl<PermissionMapper, Permission, PermissionVO> implements IPermissionService {
}
