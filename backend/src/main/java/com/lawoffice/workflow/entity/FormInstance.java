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
@TableName("wf_form_instance")
@ModuleInfo(value = "workflow:form-instance", name = "审批表单实例", description = "审批中心表单实例")
@Schema(description = "审批中心表单实例")
public class FormInstance extends BaseTenantEntity {

    private String processInstanceId;

    private String formDefinitionId;

    private String formKey;

    private String formName;

    private Integer formVersion;

    private String formDataJson;

    private String formSchemaSnapshotJson;

    private String formOptionSnapshotJson;

    private String status;

    private LocalDateTime submittedTime;
}
