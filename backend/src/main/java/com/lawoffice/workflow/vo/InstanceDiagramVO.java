package com.lawoffice.workflow.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class InstanceDiagramVO {

    private String processInstanceId;

    private String processModelId;

    private String bpmnXml;

    private List<BranchRecordVO> branchRecords = new ArrayList<>();

    private List<OperationRecordVO> operationRecords = new ArrayList<>();
}
