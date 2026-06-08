package com.lawoffice.document.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OnlyOfficePreviewConfigReq {

    @NotBlank(message = "文件ID不能为空")
    @Size(max = 64, message = "文件ID不能超过64个字符")
    private String fileId;

    @Size(max = 16, message = "预览模式不能超过16个字符")
    private String mode;
}
