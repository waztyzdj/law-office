package com.lawoffice.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.framework.entity.BaseTenantEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_branch_record")
@ModuleInfo(value = "workflow:branch", name = "审批分支", description = "审批中心条件分支命中记录")
@Schema(description = "审批中心条件分支命中记录")
public class BranchRecord extends BaseTenantEntity {

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

    private LocalDateTime matchedTime;
}
