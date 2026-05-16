package com.lawoffice.framework.req;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 分页查询请求基类
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BasePageReq extends BaseQueryReq {
    private static final long serialVersionUID = 1L;

    /**
     * 当前页码
     */
    private int pageNum = 1;

    /**
     * 每页数量
     */
    private int pageSize = 10;
}
