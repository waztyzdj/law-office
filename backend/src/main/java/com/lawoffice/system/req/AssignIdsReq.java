package com.lawoffice.system.req;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用 ID 分配请求对象。
 */
@Data
public class AssignIdsReq implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "目标ID不能为空")
    private String id;

    private List<String> ids = new ArrayList<>();
}
