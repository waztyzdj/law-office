package com.lawoffice.system.vo;

import com.lawoffice.framework.vo.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 组织机构视图对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysDepartVO extends BaseVO {
    private static final long serialVersionUID = 1L;

    /**
     * 父级ID
     */
    private String parentId;

    /**
     * 机构名称
     */
    private String departName;

    /**
     * 机构编码
     */
    private String departCode;

    /**
     * 机构类型
     */
    private String orgCategory;

    /**
     * 排序号
     */
    private Integer orderNum;

    /**
     * 描述
     */
    private String description;

    /**
     * 状态(1-正常,0-冻结)
     */
    private Integer status;

    /**
     * 删除标识(0-正常,1-已删除)
     */
    private Integer deleteFlag;
}
