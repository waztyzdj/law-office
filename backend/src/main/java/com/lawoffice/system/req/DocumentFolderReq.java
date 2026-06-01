package com.lawoffice.system.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DocumentFolderReq {

    @Size(max = 64, message = "父级ID不能超过64个字符")
    private String parentId;

    @Size(max = 32)
    private String scope;

    @Size(max = 16)
    private String shareTargetType;

    @NotBlank(message = "文件夹名称不能为空")
    @Size(max = 255, message = "文件夹名称不能超过255个字符")
    private String fileName;
}
