package com.lawoffice.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Flowable BPMN 部署结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlowableDeploymentResult {

    private String deploymentId;

    private String processDefinitionId;

    private String processDefinitionKey;

    private Integer processDefinitionVersion;
}
