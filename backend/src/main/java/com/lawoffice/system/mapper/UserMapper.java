package com.lawoffice.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lawoffice.system.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    
    /**
     * 根据用户名查询用户（包含密码字段，仅用于登录验证）
     * 注意：此方法仅用于登录认证，不要在其他业务场景使用
     *
     * @param username 用户名
     * @return 用户信息（包含密码）
     */
    @Select("SELECT * FROM sys_user WHERE username = #{username} AND delete_flag = 0")
    User selectByUsernameForLogin(String username);
}
