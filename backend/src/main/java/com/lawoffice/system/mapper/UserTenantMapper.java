package com.lawoffice.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lawoffice.system.entity.UserTenant;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserTenantMapper extends BaseMapper<UserTenant> {
}
