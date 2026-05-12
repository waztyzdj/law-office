package com.lawoffice.system.req;

import com.lawoffice.framework.req.BaseReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 租户请求对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TenantReq extends BaseReq {
    private static final long serialVersionUID = 1L;

    /**
     * 租户名称
     */
    private String name;

    /**
     * 租户编码
     */
    private String code;

    /**
     * 开始时间
     */
    private String beginDate;

    /**
     * 结束时间
     */
    private String endDate;

    /**
     * 状态(1-正常,0-冻结)
     */
    private Integer status;

    /**
     * 删除标识(0-正常,1-已删除)
     */
    private Integer deleteFlag;
}
