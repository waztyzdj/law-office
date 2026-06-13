package com.lawoffice.workflow.vo;

import lombok.Data;

import java.util.List;

@Data
public class StartFormVO {

    private String processModelId;

    private String processName;

    private String formDefinitionId;

    private String formKey;

    private String formName;

    private Integer formVersion;

    private String schemaJson;

    private String optionJson;

    private List<RuntimeFieldPermissionVO> fieldPermissions;

    private List<AssigneeSelectNodeVO> assigneeSelectNodes;
}
