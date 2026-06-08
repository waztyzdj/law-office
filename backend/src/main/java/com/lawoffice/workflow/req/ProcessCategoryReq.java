package com.lawoffice.workflow.req;

import com.lawoffice.framework.req.BaseReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProcessCategoryReq extends BaseReq {
    private String parentId;
    private String categoryCode;
    private String categoryName;
    private Integer sortOrder;
    private String status;
    private String remark;
}
