package com.lawoffice.system.req;

import com.lawoffice.framework.req.BaseReq;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 组织机构请求对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysDepartReq extends BaseReq {
    private static final long serialVersionUID = 1L;

    /**
     * 父机构ID
     */
    @Size(max = 64, message = "父机构ID不能超过64个字符")
    private String parentId;

    /**
     * 机构/部门名称
     */
    @NotBlank(message = "机构名称不能为空")
    @Size(max = 64, message = "机构名称不能超过64个字符")
    private String departName;

    /**
     * 英文名
     */
    @Size(max = 1024, message = "英文名不能超过1024个字符")
    private String departNameEn;

    /**
     * 缩写
     */
    @Size(max = 1024, message = "缩写不能超过1024个字符")
    private String departNameAbbr;

    /**
     * 排序
     */
    private Integer departOrder;

    /**
     * 描述
     */
    @Size(max = 1024, message = "描述不能超过1024个字符")
    private String description;

    /**
     * 类型
     */
    @NotBlank(message = "机构类型不能为空")
    @Pattern(regexp = "^[1-8]$", message = "机构类型参数不正确")
    private String orgType;

    /**
     * 机构编码
     */
    @NotBlank(message = "机构编码不能为空")
    @Size(max = 64, message = "机构编码不能超过64个字符")
    @Pattern(regexp = "^[A-Za-z0-9_.:-]+$", message = "机构编码只能包含字母、数字、下划线、中划线、点和冒号")
    private String orgCode;

    /**
     * 手机号
     */
    @Size(max = 64, message = "手机号不能超过64个字符")
    private String mobile;

    /**
     * 传真
     */
    @Size(max = 64, message = "传真不能超过64个字符")
    private String fax;

    /**
     * 地址
     */
    @Size(max = 64, message = "地址不能超过64个字符")
    private String address;

    /**
     * 备注
     */
    @Size(max = 1024, message = "备注不能超过1024个字符")
    private String memo;

    /**
     * 状态(1-启用,0-停用)
     */
    @Pattern(regexp = "^[01]$", message = "状态参数不正确")
    private String status;

    /**
     * 删除标识(0-正常,1-已删除)
     */
    private Integer deleteFlag;
}
