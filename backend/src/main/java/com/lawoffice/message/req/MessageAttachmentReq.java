package com.lawoffice.message.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MessageAttachmentReq {

    @NotBlank(message = "文件ID不能为空")
    private String fileId;

    @Size(max = 1024, message = "文件名称不能超过1024个字符")
    private String fileName;

    @Size(max = 64, message = "文件类型不能超过64个字符")
    private String fileType;

    private Double fileSize;

    private Integer sortOrder;
}
