package com.lawoffice.workflow.vo;

import lombok.Data;

import java.util.List;

@Data
public class AdminMonitorDetailVO {

    private InstanceDetailVO detail;

    private List<AdminOperationRecordVO> adminOperationRecords;
}
