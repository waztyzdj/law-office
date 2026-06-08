package com.lawoffice.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Flowable 当前任务摘要。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlowableTaskInfo {

    private String taskId;

    private String taskDefinitionKey;

    private String taskName;

    private String assignee;

    private String owner;
}
