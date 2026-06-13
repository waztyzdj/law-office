package com.lawoffice.workflow.req;

import lombok.Data;

import java.util.List;

@Data
public class StartProcessReq {

    private String processModelId;

    private String instanceTitle;

    private String businessKey;

    private String formDataJson;

    private List<SelectedAssigneeReq> selectedAssignees;
}
