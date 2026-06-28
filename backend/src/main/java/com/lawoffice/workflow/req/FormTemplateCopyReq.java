package com.lawoffice.workflow.req;

import com.lawoffice.framework.req.BaseReq;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class FormTemplateCopyReq extends BaseReq {

    @Size(max = 64, message = "来源表单ID不能超过64个字符")
    private String sourceFormDefinitionId;

    @Size(max = 64, message = "流程分类ID不能超过64个字符")
    private String categoryId;

    @NotBlank(message = "表单编码不能为空")
    @Size(max = 64, message = "表单编码不能超过64个字符")
    private String formKey;

    @NotBlank(message = "表单名称不能为空")
    @Size(max = 100, message = "表单名称不能超过100个字符")
    private String formName;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;
}
