package com.lawoffice.system.service.impl;

import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.system.entity.SysCategory;
import com.lawoffice.system.mapper.SysCategoryMapper;
import com.lawoffice.system.service.ISysCategoryService;
import com.lawoffice.system.vo.SysCategoryVO;
import org.springframework.stereotype.Service;

@Service
public class SysCategoryServiceImpl extends BaseServiceImpl<SysCategoryMapper, SysCategory, SysCategoryVO> implements ISysCategoryService {
}
