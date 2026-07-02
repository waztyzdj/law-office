package com.lawoffice.workflow.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lawoffice.framework.vo.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class ArchiveRecordVO extends BaseVO {

    private String tenantId;

    private String processInstanceId;

    private String processModelId;

    private String categoryId;

    private String categoryName;

    private String processKey;

    private String processName;

    private Integer processVersion;

    private String formInstanceId;

    private String formDefinitionId;

    private String instanceNo;

    private String instanceTitle;

    private String starterUserId;

    private String starterUsername;

    private String starterRealname;

    private String instanceStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime processStartTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime processEndTime;

    private String archiveSource;

    private String archiveReason;

    private String archiverUserId;

    private String archiverUsername;

    private String archiverRealname;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime archiveTime;
}
