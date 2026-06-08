package com.lawoffice.workflow.vo;

import com.lawoffice.framework.vo.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProcessNodeConfigVO extends BaseVO {
    private String tenantId;
    private String processModelId;
    private String nodeId;
    private String nodeName;
    private String nodeType;
    private String assigneeType;
    private String assigneeJson;
    private Integer allowTransfer;
    private Integer allowAddSign;
    private Integer allowReturn;
    private Integer sortOrder;
}
