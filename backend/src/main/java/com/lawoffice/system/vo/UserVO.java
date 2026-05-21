package com.lawoffice.system.vo;

import com.lawoffice.framework.vo.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户视图对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserVO extends BaseVO {
    private static final long serialVersionUID = 1L;

    /**
     * 用户名
     */
    private String username;

    /**
     * 真实姓名
     */
    private String realname;

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
    private String email;

    /**
     * 电话
     */
    private String phone;

    /**
     * 工号
     */
    private String workNo;

    /**
     * 职务
     */
    private String post;

    /**
     * 座机号
     */
    private String telephone;

    /**
     * 身份证号
     */
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
