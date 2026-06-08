package com.lawoffice.workflow.req;

import com.lawoffice.framework.req.BaseReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ProcessModelReq extends BaseReq {
    private String categoryId;
    private String formDefinitionId;
    private String processKey;
    private String processName;
    private Integer version;
    private String designerType;
    private String nodeJson;
    private String bpmnXml;
    private String status;
    private String startScopeType;
    private String remark;
}
