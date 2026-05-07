package com.lawoffice.system.service.impl;

import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.system.entity.SysDictItem;
import com.lawoffice.system.mapper.SysDictItemMapper;
import com.lawoffice.system.service.ISysDictItemService;
import org.springframework.stereotype.Service;

@Service
public class SysDictItemServiceImpl extends BaseServiceImpl<SysDictItemMapper, SysDictItem> implements ISysDictItemService {
}
