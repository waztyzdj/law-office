package com.lawoffice.system.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DocumentRenameReq {

    @NotBlank(message = "文件ID不能为空")
    @Size(max = 64, message = "文件ID不能超过64个字符")
    private String id;

    @NotBlank(message = "名称不能为空")
    @Size(max = 255, message = "名称不能超过255个字符")
    private String fileName;
}
