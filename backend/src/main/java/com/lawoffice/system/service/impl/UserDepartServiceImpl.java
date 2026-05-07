package com.lawoffice.system.service.impl;

import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.system.entity.UserDepart;
import com.lawoffice.system.mapper.UserDepartMapper;
import com.lawoffice.system.service.IUserDepartService;
import org.springframework.stereotype.Service;

@Service
public class UserDepartServiceImpl extends BaseServiceImpl<UserDepartMapper, UserDepart> implements IUserDepartService {
}
