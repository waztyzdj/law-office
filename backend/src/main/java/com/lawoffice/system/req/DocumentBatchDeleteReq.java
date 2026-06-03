package com.lawoffice.system.req;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class DocumentBatchDeleteReq {

    @NotEmpty(message = "文件ID不能为空")
    @Size(max = 100, message = "单次最多删除100个文档")
    private List<@Size(max = 64, message = "文件ID不能超过64个字符") String> ids;
}
