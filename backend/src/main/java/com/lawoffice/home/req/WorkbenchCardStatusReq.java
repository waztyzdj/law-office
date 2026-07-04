package com.lawoffice.home.req;

import com.lawoffice.framework.req.BaseReq;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WorkbenchCardStatusReq extends BaseReq {
    @NotBlank(message = "状态不能为空")
    private String status;
}
