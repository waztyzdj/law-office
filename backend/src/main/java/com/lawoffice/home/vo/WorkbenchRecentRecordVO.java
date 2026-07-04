package com.lawoffice.home.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lawoffice.framework.vo.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class WorkbenchRecentRecordVO extends BaseVO {
    private String tenantId;
    private String userId;
    private String recordType;
    private String recordKey;
    private String moduleCode;
    private String bizId;
    private String title;
    private String targetType;
    private String targetPath;
    private String targetParamsJson;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime sourceTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime lastVisitTime;
    private Integer visitCount;
}
