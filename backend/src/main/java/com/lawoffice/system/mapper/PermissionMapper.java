package com.lawoffice.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lawoffice.system.entity.Permission;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PermissionMapper extends BaseMapper<Permission> {
}
