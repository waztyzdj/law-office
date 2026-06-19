package com.lawoffice.workflow.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lawoffice.framework.vo.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProcessModelVO extends BaseVO {
    private String tenantId;
    private String categoryId;
    private String formDefinitionId;
    private String processKey;
    private String processName;
    private Integer version;
    private String designerType;
    private String nodeJson;
    private String bpmnXml;
    private String bpmnSecurityStatus;
    private String bpmnSecurityMessage;
    private String status;
    private String startScopeType;
    private String flowableDeploymentId;
    private String flowableProcessDefinitionId;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime publishedTime;
    private String remark;
}
