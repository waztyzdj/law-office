package com.lawoffice.system.req;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DocumentUploadReq {

    @Size(max = 64, message = "父级ID不能超过64个字符")
    private String parentId;

    @Size(max = 32)
    private String scope;

    @Size(max = 16)
    private String shareTargetType;

    @Size(max = 64)
    private String shareTargetId;
}
