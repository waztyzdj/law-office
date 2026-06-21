package com.lawoffice.workflow.dto;

import lombok.Data;

/**
 * 条件分支命中结果。
 */
@Data
public class BranchMatchResult {

    private String gatewayNodeId;

    private String gatewayNodeName;

    private String branchId;

    private String branchName;

    private String targetNodeId;

    private String targetNodeName;

    private String conditionSnapshotJson;
}
