package com.lawoffice.system.req;

import com.lawoffice.framework.req.BasePageReq;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class DocumentPageReq extends BasePageReq {

    @Size(max = 32, message = "范围不能超过32个字符")
    private String scope;

    @Size(max = 64, message = "父级ID不能超过64个字符")
    private String parentId;

    @Size(max = 255, message = "关键字不能超过255个字符")
    private String keyword;

    @Size(max = 64, message = "文件类型不能超过64个字符")
    private String fileType;

    @Size(max = 16, message = "共享目标类型不能超过16个字符")
    private String shareTargetType;

    private Boolean folderOnly;

    @Size(max = 64, message = "共享目标ID不能超过64个字符")
    private String shareTargetId;
}
