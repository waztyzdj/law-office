package com.lawoffice.workflow.req;

import lombok.Data;

import java.util.List;

@Data
public class SelectedAssigneeReq {

    private String nodeId;

    private List<String> userIds;
}
