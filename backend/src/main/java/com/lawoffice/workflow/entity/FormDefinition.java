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
@TableName("wf_form_definition")
@ModuleInfo(value = "workflow:form", name = "审批表单", description = "审批中心表单定义版本")
@Schema(description = "审批中心表单定义版本")
public class FormDefinition extends BaseTenantEntity {

    private String categoryId;

    private String formKey;

    private String formName;

    private Integer version;

    private String schemaJson;

    private String optionJson;

    private String status;

    private LocalDateTime publishedTime;

    private String remark;
}
