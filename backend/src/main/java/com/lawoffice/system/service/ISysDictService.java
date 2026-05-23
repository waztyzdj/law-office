package com.lawoffice.system.service;

import com.lawoffice.framework.service.IBaseService;
import com.lawoffice.system.entity.SysDict;
import com.lawoffice.system.vo.DictOptionVO;
import com.lawoffice.system.vo.SysDictVO;
import com.lawoffice.framework.result.BaseResult;

import java.util.List;

public interface ISysDictService extends IBaseService<SysDict, SysDictVO> {
    BaseResult<List<DictOptionVO>> listOptionsByCode(String dictCode);
}
