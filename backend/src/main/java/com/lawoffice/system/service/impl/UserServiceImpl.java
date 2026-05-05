package com.lawoffice.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.mapper.UserMapper;
import com.lawoffice.system.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class UserServiceImpl extends BaseServiceImpl<User> implements IUserService {

    @Autowired
    private UserMapper userMapper;
    
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Autowired
    public UserServiceImpl(UserMapper userMapper) {
        super(userMapper, User.class);
        this.userMapper = userMapper;
    }

    @Override
    protected void fillEntity(User entity, RequestContext context, boolean isCreate) {
        if (isCreate) {
            entity.setCreateTime(java.time.LocalDateTime.now());
            entity.setCreateBy(context.getUsername());
            if (StringUtils.hasText(entity.getPassword())) {
                entity.setPassword(passwordEncoder.encode(entity.getPassword()));
            }
            if (entity.getStatus() == null) {
                entity.setStatus(1);
            }
        } else {
            entity.setUpdateTime(java.time.LocalDateTime.now());
            entity.setUpdateBy(context.getUsername());
            if (StringUtils.hasText(entity.getPassword())) {
                entity.setPassword(passwordEncoder.encode(entity.getPassword()));
            }
        }
        if (entity.getDeleteFlag() == null) {
            entity.setDeleteFlag(0);
        }
    }

    @Override
    protected void doBeforeSave(BaseDTO<User> saveDTO) {
        User user = saveDTO.getEntity();
        
        if (user.getId() == null || user.getId().isEmpty()) {
            log.info("新增用户，用户名: {}", user.getUsername());
            
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(User::getUsername, user.getUsername())
                   .eq(User::getDeleteFlag, 0);
            if (userMapper.selectCount(wrapper) > 0) {
                throw new RuntimeException("用户名已存在");
            }
            
            if (StringUtils.hasText(user.getPhone())) {
                LambdaQueryWrapper<User> phoneWrapper = new LambdaQueryWrapper<>();
                phoneWrapper.eq(User::getPhone, user.getPhone())
                           .eq(User::getDeleteFlag, 0);
                if (userMapper.selectCount(phoneWrapper) > 0) {
                    throw new RuntimeException("手机号已被使用");
                }
            }
            
            if (StringUtils.hasText(user.getIdCard())) {
                LambdaQueryWrapper<User> idCardWrapper = new LambdaQueryWrapper<>();
                idCardWrapper.eq(User::getIdCard, user.getIdCard())
                            .eq(User::getDeleteFlag, 0);
                if (userMapper.selectCount(idCardWrapper) > 0) {
                    throw new RuntimeException("身份证号已被使用");
                }
            }
        } else {
            log.info("修改用户，用户ID: {}", user.getId());
        }
    }

    @Override
    protected void doAfterSave(BaseDTO<User> saveDTO, User entity) {
        log.info("保存用户成功，用户ID: {}", entity.getId());
    }

    @Override
    public boolean verifyPassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }

    @Override
    public void resetPassword(String userId, String newPassword) {
        User user = new User();
        user.setId(userId);
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setUpdateTime(java.time.LocalDateTime.now());
        user.setUpdateBy("system");
        userMapper.updateById(user);
        log.info("重置用户密码成功，用户ID: {}", userId);
    }
}
