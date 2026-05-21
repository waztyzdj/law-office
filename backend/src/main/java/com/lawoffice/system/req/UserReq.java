package com.lawoffice.system.req;

import com.lawoffice.framework.req.BaseReq;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户请求对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserReq extends BaseReq {
    private static final long serialVersionUID = 1L;

    /**
     * 用户名
     */
    @Size(max = 50, message = "用户名不能超过50个字符")
    private String username;

    /**
     * 真实姓名
     */
    @Size(max = 50, message = "真实姓名不能超过50个字符")
    private String realname;

    /**
     * 密码
     */
    @Size(max = 20, message = "密码不能超过20个字符")
    private String password;

    /**
     * 头像
     */
    private String avatar;

    /**
     * 生日
     */
    private String birthday;

    /**
     * 性别(0-默认,1-男,2-女)
     */
    private Integer sex;

    /**
     * 邮箱
     */
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱不能超过100个字符")
    private String email;

    /**
     * 电话
     */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号码格式不正确")
    private String phone;

    /**
     * 工号
     */
    @Size(max = 50, message = "工号不能超过50个字符")
    private String workNo;

    /**
     * 职务
     */
    @Size(max = 50, message = "职务不能超过50个字符")
    private String post;

    /**
     * 座机号
     */
    @Pattern(regexp = "^$|^(?:\\d{3,4}-?)?\\d{7,8}$", message = "座机号码格式不正确")
    private String telephone;

    /**
     * 身份证号
     */
    @Pattern(regexp = "^$|(^\\d{15}$)|(^\\d{17}[\\dXx]$)", message = "身份证号格式不正确")
    private String idCard;

    /**
     * 状态(1-正常,2-冻结)
     */
    private Integer status;

    /**
     * 身份（1普通成员 2上级）
     */
    private Integer userIdentity;

    /**
     * 负责部门
     */
    private String departIds;

    /**
     * 设备ID
     */
    private String clientId;

    /**
     * 上次登录选择租户ID
     */
    private String loginTenantId;

    /**
     * 流程入职离职状态
     */
    private String bpmStatus;

    /**
     * 删除标识(0-正常,1-已删除)
     */
    private Integer deleteFlag;

    /**
     * 租户ID
     */
    private String tenantId;
}
