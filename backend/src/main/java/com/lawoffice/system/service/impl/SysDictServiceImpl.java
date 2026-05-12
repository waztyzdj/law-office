package com.lawoffice.system.service.impl;

import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.system.entity.SysDict;
import com.lawoffice.system.mapper.SysDictMapper;
import com.lawoffice.system.service.ISysDictService;
import com.lawoffice.system.vo.SysDictVO;
import org.springframework.stereotype.Service;

@Service
public class SysDictServiceImpl extends BaseServiceImpl<SysDictMapper, SysDict, SysDictVO> implements ISysDictService {
}
