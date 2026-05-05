package com.lawoffice.system.service;

import com.lawoffice.framework.service.IBaseService;
import com.lawoffice.system.entity.User;

public interface IUserService extends IBaseService<User> {

    boolean verifyPassword(String rawPassword, String encodedPassword);

    void resetPassword(String userId, String newPassword);
}
