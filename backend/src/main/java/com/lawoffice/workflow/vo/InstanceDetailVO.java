package com.lawoffice.workflow.vo;

import lombok.Data;

import java.util.List;

@Data
public class InstanceDetailVO {

    private ProcessInstanceVO processInstance;

    private FormInstanceVO formInstance;

    private List<RuntimeTaskVO> currentTasks;

    private List<OperationRecordVO> records;
}
