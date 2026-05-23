package com.lawoffice.system.req;

import com.lawoffice.framework.req.BaseReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 字典请求对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysDictReq extends BaseReq {
    private static final long serialVersionUID = 1L;

    /**
     * 字典名称
     */
    private String dictName;

    /**
     * 字典编码
     */
    private String dictCode;

    /**
     * 描述
     */
    private String description;

    /**
     * 状态(1-正常,0-冻结)
     */

    /**
     * 删除标识(0-正常,1-已删除)
     */
    private Integer deleteFlag;
}
