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
@TableName("wf_cc_record")
@ModuleInfo(value = "workflow:cc", name = "审批抄送", description = "审批中心抄送记录")
@Schema(description = "审批中心抄送记录")
public class CcRecord extends BaseTenantEntity {

    private String processInstanceId;

    private String processModelId;

    private String taskId;

    private String nodeId;

    private String nodeName;

    private String triggerAction;

    private String sourceType;

    private String sourceId;

    private String receiverUserId;

    private String receiverUsername;

    private String receiverRealname;

    private String status;

    private LocalDateTime readTime;

    private String messageId;

    private String remark;
}
