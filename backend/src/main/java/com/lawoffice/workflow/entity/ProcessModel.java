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
@TableName("wf_process_model")
@ModuleInfo(value = "workflow:process", name = "审批流程", description = "审批中心流程模型版本")
@Schema(description = "审批中心流程模型版本")
public class ProcessModel extends BaseTenantEntity {

    private String categoryId;

    private String formDefinitionId;

    private String processKey;

    private String processName;

    private Integer version;

    private String designerType;

    private String nodeJson;

    private String bpmnXml;

    private String status;

    private String startScopeType;

    private String flowableDeploymentId;

    private String flowableProcessDefinitionId;

    private LocalDateTime publishedTime;

    private String remark;
}
