package com.lawoffice.system.controller;

import com.lawoffice.system.annotation.RequiresPermission;
import com.lawoffice.framework.dto.BaseResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 权限测试Controller
 * 用于演示Shiro权限控制的使用方式
 */
@Slf4j
@RestController
@RequestMapping("/test/permission")
@Tag(name = "权限测试", description = "演示Shiro权限控制功能")
public class PermissionTestController {

    /**
     * 测试接口 - 需要user:view权限
     */
    @GetMapping("/view")
    @Operation(summary = "查看测试", description = "需要user:view权限")
    @RequiresPermission({"user:view"})
    public BaseResult<String> viewTest() {
        log.info("执行查看操作");
        return BaseResult.success("您有查看权限");
    }

    /**
     * 测试接口 - 需要user:add权限
     */
    @PostMapping("/add")
    @Operation(summary = "添加测试", description = "需要user:add权限")
    @RequiresPermission({"user:add"})
    public BaseResult<String> addTest() {
        log.info("执行添加操作");
        return BaseResult.success("您有添加权限");
    }

    /**
     * 测试接口 - 需要user:edit权限
     */
    @PutMapping("/edit")
    @Operation(summary = "编辑测试", description = "需要user:edit权限")
    @RequiresPermission({"user:edit"})
    public BaseResult<String> editTest() {
        log.info("执行编辑操作");
        return BaseResult.success("您有编辑权限");
    }

    /**
     * 测试接口 - 需要user:delete权限
     */
    @DeleteMapping("/delete")
    @Operation(summary = "删除测试", description = "需要user:delete权限")
    @RequiresPermission({"user:delete"})
    public BaseResult<String> deleteTest() {
        log.info("执行删除操作");
        return BaseResult.success("您有删除权限");
    }

    /**
     * 测试接口 - 需要多个权限(AND逻辑)
     */
    @PostMapping("/complex-and")
    @Operation(summary = "复杂操作测试-AND", description = "需要同时拥有user:add和user:view权限")
    @RequiresPermission(value = {"user:add", "user:view"}, logical = RequiresPermission.Logical.AND)
    public BaseResult<String> complexAndTest() {
        log.info("执行复杂操作(AND逻辑)");
        return BaseResult.success("您同时拥有添加和查看权限");
    }

    /**
     * 测试接口 - 需要多个权限(OR逻辑)
     */
    @PostMapping("/complex-or")
    @Operation(summary = "复杂操作测试-OR", description = "拥有user:add或user:edit任一权限即可")
    @RequiresPermission(value = {"user:add", "user:edit"}, logical = RequiresPermission.Logical.OR)
    public BaseResult<String> complexOrTest() {
        log.info("执行复杂操作(OR逻辑)");
        return BaseResult.success("您拥有添加或编辑权限");
    }

    /**
     * 测试接口 - 不需要权限
     */
    @GetMapping("/public")
    @Operation(summary = "公开接口测试", description = "只需要登录，不需要特定权限")
    public BaseResult<String> publicTest() {
        log.info("执行公开操作");
        return BaseResult.success("这是一个公开接口，只需登录即可访问");
    }
}
