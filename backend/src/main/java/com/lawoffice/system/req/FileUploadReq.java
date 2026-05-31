package com.lawoffice.system.req;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FileUploadReq {

    @Size(max = 64, message = "业务类型不能超过64个字符")
    private String bizType;

    @Size(max = 64, message = "业务ID不能超过64个字符")
    private String bizId;

    @Size(max = 1024, message = "文件描述不能超过1024个字符")
    private String description;
}
