package com.lawoffice.framework.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lawoffice.framework.entity.SysLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LogMapper extends BaseMapper<SysLog> {
}
