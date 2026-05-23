package com.lawoffice.system.vo;

import com.lawoffice.framework.vo.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 通用分类视图对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysCategoryVO extends BaseVO {
    private static final long serialVersionUID = 1L;

    /**
     * 父级ID
     */
    private String pid;

    /**
     * 名称
     */
    private String name;

    /**
     * 编码
     */
    private String code;

    /**
     * 是否有子节点
     */
    private String hasChild;
}
