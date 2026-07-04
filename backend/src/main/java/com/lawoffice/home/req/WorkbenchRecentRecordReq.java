package com.lawoffice.home.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkbenchRecentRecordReq {
    @NotBlank(message = "记录类型不能为空")
    private String recordType;
    @Size(max = 64, message = "模块编码不能超过64个字符")
    private String moduleCode;
    private String bizId;
    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题不能超过200个字符")
    private String title;
    private String targetType;
    @Size(max = 512, message = "目标路径不能超过512个字符")
    private String targetPath;
    private String targetParamsJson;
    private LocalDateTime sourceTime;
}
