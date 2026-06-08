package com.lawoffice.workflow.vo;

import com.lawoffice.framework.vo.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProcessCategoryVO extends BaseVO {
    private String tenantId;
    private String parentId;
    private String categoryCode;
    private String categoryName;
    private Integer sortOrder;
    private String status;
    private String remark;
}
