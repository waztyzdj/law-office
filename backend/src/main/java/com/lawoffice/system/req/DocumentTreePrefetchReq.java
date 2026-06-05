package com.lawoffice.system.req;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class DocumentTreePrefetchReq {

    @Size(max = 32, message = "范围不能超过32个字符")
    private String scope;

    @Size(max = 100, message = "父级目录一次最多预取100个")
    private List<@Size(max = 64, message = "父级ID不能超过64个字符") String> parentIds;

    @Size(max = 16, message = "共享目标类型不能超过16个字符")
    private String shareTargetType;

    @Size(max = 64, message = "共享目标ID不能超过64个字符")
    private String shareTargetId;
}
