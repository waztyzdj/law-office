package com.lawoffice.workflow.vo;

import lombok.Data;

import java.util.List;

@Data
public class TaskFormVO {

    private String taskId;

    private String processInstanceId;

    private String instanceNo;

    private String instanceTitle;

    private String nodeId;

    private String taskName;

    private String taskType;

    private String parentTaskId;

    private String formInstanceId;

    private String formDefinitionId;

    private String formKey;

    private String formName;

    private Integer formVersion;

    private String schemaJson;

    private String optionJson;

    private String formDataJson;

    private TaskActionPermissionVO actionPermissions;

    private List<TaskReturnNodeVO> returnNodes;

    private List<RuntimeFieldPermissionVO> fieldPermissions;
}
