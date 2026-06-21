package com.lawoffice.workflow.req;

import lombok.Data;

@Data
public class AssigneePreviewReq {

    private String processModelId;

    private String taskId;

    private String formDataJson;
}
