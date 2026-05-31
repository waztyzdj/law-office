package com.lawoffice.system.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FileRelationReq {

    @NotBlank(message = "文件ID不能为空")
    private String fileId;

    @NotBlank(message = "业务类型不能为空")
    @Size(max = 64, message = "业务类型不能超过64个字符")
    private String bizType;

    @NotBlank(message = "业务ID不能为空")
    @Size(max = 64, message = "业务ID不能超过64个字符")
    private String bizId;

    private Integer relationType;

    private Integer sortOrder;
}
