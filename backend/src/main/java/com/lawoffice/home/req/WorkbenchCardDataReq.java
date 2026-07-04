package com.lawoffice.home.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class WorkbenchCardDataReq {
    @NotBlank(message = "卡片编码不能为空")
    private String cardCode;
    private Integer limit;
    private String timeRange;
    private Map<String, Object> params;
}
