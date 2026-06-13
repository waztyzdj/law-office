package com.lawoffice.workflow.req;

import lombok.Data;

import java.util.List;

@Data
public class TaskActionReq {

    private String taskId;

    private String targetUserId;

    private String targetNodeId;

    private String comment;

    private String formDataJson;

    private List<SelectedAssigneeReq> selectedAssignees;
}
