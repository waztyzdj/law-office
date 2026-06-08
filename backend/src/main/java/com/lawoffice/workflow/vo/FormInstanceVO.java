package com.lawoffice.workflow.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lawoffice.framework.vo.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class FormInstanceVO extends BaseVO {
    private String tenantId;
    private String processInstanceId;
    private String formDefinitionId;
    private String formKey;
    private String formName;
    private Integer formVersion;
    private String formDataJson;
    private String formSchemaSnapshotJson;
    private String formOptionSnapshotJson;
    private String status;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime submittedTime;
}
