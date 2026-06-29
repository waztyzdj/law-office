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
@TableName("wf_admin_operation_record")
@ModuleInfo(value = "workflow:monitor", name = "流程监控维护记录", description = "审批中心流程监控维护记录")
@Schema(description = "审批中心流程监控维护记录")
public class AdminOperationRecord extends BaseTenantEntity {

    private String processInstanceId;

    private String taskId;

    private String operationType;

    private String operationReason;

    private String beforeSnapshotJson;

    private String afterSnapshotJson;

    private String operatorUserId;

    private String operatorUsername;

    private String operatorRealname;

    private LocalDateTime operateTime;

    private String status;

    private String errorMessage;
}
