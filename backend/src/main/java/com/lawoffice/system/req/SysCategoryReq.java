package com.lawoffice.system.req;

import com.lawoffice.framework.req.BaseReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 通用分类请求对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysCategoryReq extends BaseReq {
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
}
