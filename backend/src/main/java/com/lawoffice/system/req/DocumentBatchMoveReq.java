package com.lawoffice.system.req;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class DocumentBatchMoveReq {

    @NotEmpty(message = "文件ID不能为空")
    @Size(max = 100, message = "单次最多移动100个文档")
    private List<@Size(max = 64, message = "文件ID不能超过64个字符") String> ids;

    @Size(max = 64, message = "父级ID不能超过64个字符")
    private String parentId;

    @Size(max = 32, message = "范围不能超过32个字符")
    private String scope;

    @Size(max = 16, message = "共享目标类型不能超过16个字符")
    private String shareTargetType;
}
