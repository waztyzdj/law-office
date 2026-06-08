package com.lawoffice.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Flowable 流程启动结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlowableStartResult {

    private String processInstanceId;

    private String processDefinitionId;
}
