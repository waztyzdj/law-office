package com.lawoffice.framework.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * 通用查询请求 DTO
 * 用于 Swagger 文档展示查询参数
 */
@Data
@Schema(description = "通用查询请求")
public class BaseQueryReq {

    @Schema(description = "查询条件（键值对）", example = "{\"name\": \"张三\", \"status\": 1}")
    private Map<String, Object> queryParams;

    @Schema(description = "排序字段", example = "createTime")
    private String sortField;

    @Schema(description = "排序方向（asc/desc）", example = "desc")
    private String sortOrder = "desc";
}
