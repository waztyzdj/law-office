package com.lawoffice.system.controller;

import com.lawoffice.framework.controller.BaseController;
import com.lawoffice.system.entity.User;
import com.lawoffice.system.service.IUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
@Tag(name = "用户管理", description = "系统用户信息管理")
public class UserController extends BaseController<IUserService, User> {

    @Autowired
    public UserController(IUserService userService) {
        this.baseService = userService;
    }
}
