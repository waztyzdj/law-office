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
@TableName("wf_approval_record")
@ModuleInfo(value = "workflow:record", name = "审批记录", description = "审批中心审批记录")
@Schema(description = "审批中心审批记录")
public class OperationRecord extends BaseTenantEntity {

    private String processInstanceId;

    private String taskId;

    private String flowableTaskId;

    private String nodeId;

    private String nodeName;

    private String action;

    private String operatorUserId;

    private String operatorUsername;

    private String operatorRealname;

    private String targetUserId;

    private String targetUsername;

    private String targetRealname;

    private String targetNodeId;

    private String targetNodeName;

    private String comment;

    private String formDataSnapshotJson;

    private LocalDateTime operateTime;
}
