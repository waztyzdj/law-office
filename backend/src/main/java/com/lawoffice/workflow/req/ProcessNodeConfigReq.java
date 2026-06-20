package com.lawoffice.workflow.req;

import com.lawoffice.framework.req.BaseReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProcessNodeConfigReq extends BaseReq {
    private String processModelId;
    private String nodeId;
    private String nodeName;
    private String nodeType;
    private String assigneeType;
    private String assigneeJson;
    private String approvalMode;
    private String assigneeResolveMode;
    private String rejectPolicy;
    private String branchJson;
    private String ccJson;
    private String timeoutJson;
    private String attachmentJson;
    private Integer allowTransfer;
    private Integer allowAddSign;
    private Integer allowReturn;
    private Integer sortOrder;
}
