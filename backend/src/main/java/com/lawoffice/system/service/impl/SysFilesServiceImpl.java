package com.lawoffice.system.service.impl;

import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.system.entity.SysFiles;
import com.lawoffice.system.mapper.SysFilesMapper;
import com.lawoffice.system.service.ISysFilesService;
import org.springframework.stereotype.Service;

@Service
public class SysFilesServiceImpl extends BaseServiceImpl<SysFilesMapper, SysFiles> implements ISysFilesService {
}
