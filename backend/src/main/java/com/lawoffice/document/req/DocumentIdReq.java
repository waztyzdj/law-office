package com.lawoffice.document.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DocumentIdReq {

    @NotBlank(message = "ID不能为空")
    @Size(max = 64, message = "ID不能超过64个字符")
    private String id;
}
