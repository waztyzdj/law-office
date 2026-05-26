package com.lawoffice.system.vo;

import com.lawoffice.framework.vo.TreeVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 组织机构视图对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysDepartVO extends TreeVO<SysDepartVO> {
    private static final long serialVersionUID = 1L;

    /**
     * 机构/部门名称
     */
    private String departName;

    /**
     * 英文名
     */
    private String departNameEn;

    /**
     * 缩写
     */
    private String departNameAbbr;

    /**
     * 排序
     */
    private Integer departOrder;

    /**
     * 描述
     */
    private String description;

    /**
     * 类型
     */
    private String orgType;

    /**
     * 机构编码
     */
    private String orgCode;

    /**
     * 手机号
     */
    private String mobile;

    /**
     * 传真
     */
    private String fax;

    /**
     * 地址
     */
    private String address;

    /**
     * 备注
     */
    private String memo;

    /**
     * 状态(1-启用,0-停用)
     */
    private String status;

    /**
     * 删除标识(0-正常,1-已删除)
     */
    private Integer deleteFlag;

    /**
     * 是否叶子节点
     */
    private Boolean izLeaf;
}
