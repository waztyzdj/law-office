package com.lawoffice.workflow.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lawoffice.framework.vo.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class StartedInstanceVO extends BaseVO {

    private String processModelId;

    private String processName;

    private String formInstanceId;

    private String formDefinitionId;

    private String formName;

    private String instanceNo;

    private String instanceTitle;

    private String businessKey;

    private String status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime endTime;

    private String currentTaskNames;

    private String currentAssigneeNames;
}
