package com.lawoffice.system.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class DocumentShareReq {

    @NotBlank(message = "文件ID不能为空")
    @Size(max = 64, message = "文件ID不能超过64个字符")
    private String fileId;

    @Valid
    private List<DocumentShareTargetReq> targets = new ArrayList<>();

    private LocalDateTime expireTime;

    @Size(max = 8, message = "下载开关不能超过8个字符")
    private String enableDown;

    @Size(max = 8, message = "修改开关不能超过8个字符")
    private String enableUpdat;
}
