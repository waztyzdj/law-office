package com.lawoffice.system.vo;

import com.lawoffice.framework.vo.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 租户视图对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TenantVO extends BaseVO {
    private static final long serialVersionUID = 1L;

    /**
     * 租户名称
     */
    private String name;

    /**
     * 开始时间
     */
    private LocalDateTime beginDate;

    /**
     * 结束时间
     */
    private LocalDateTime endDate;

    /**
     * 状态(1-正常,0-冻结)
     */
    private Integer status;

    /**
     * 所属行业
     */
    private String trade;

    /**
     * 公司规模
     */
    private String companySize;

    /**
     * 公司地址
     */
    private String companyAddress;

    /**
     * 公司Logo
     */
    private String companyLogo;

    /**
     * 门牌号
     */
    private String houseNumber;

    /**
     * 工作地点
     */
    private String workPlace;

    /**
     * 二级域名
     */
    private String secondaryDomain;

    /**
     * 登录背景图
     */
    private String loginBkgdImg;

    /**
     * 职级
     */
    private String position;

    /**
     * 部门
     */
    private String department;

    /**
     * 是否允许申请管理者(1-允许,0-不允许)
     */
    private Integer applyStatus;
}
