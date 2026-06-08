package com.lawoffice.workflow.req;

import com.lawoffice.framework.req.BaseReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FormDefinitionReq extends BaseReq {
    private String categoryId;
    private String formKey;
    private String formName;
    private Integer version;
    private String schemaJson;
    private String optionJson;
    private String status;
    private String remark;
}
