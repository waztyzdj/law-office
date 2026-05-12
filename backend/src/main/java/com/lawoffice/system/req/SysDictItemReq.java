package com.lawoffice.system.req;

import com.lawoffice.framework.req.BaseReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典明细请求对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysDictItemReq extends BaseReq {
    private static final long serialVersionUID = 1L;

    /**
     * 字典ID
     */
    private String dictId;

    /**
     * 字典项文本
     */
    private String itemText;

    /**
     * 字典项值
     */
    private String itemValue;

    /**
     * 描述
     */
    private String description;

    /**
     * 排序号
     */
    private Integer sortOrder;

    /**
     * 状态(1-正常,0-冻结)
     */
    private Integer status;

    /**
     * 删除标识(0-正常,1-已删除)
     */
    private Integer deleteFlag;
}
