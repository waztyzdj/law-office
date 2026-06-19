package com.lawoffice.workflow.req;

import com.lawoffice.framework.req.BaseReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class BranchRecordReq extends BaseReq {
    private String processInstanceId;
    private String processModelId;
    private String sourceNodeId;
    private String sourceNodeName;
    private String branchId;
    private String branchName;
    private String targetNodeId;
    private String targetNodeName;
    private String conditionSnapshotJson;
    private String formDataSnapshotJson;
}
