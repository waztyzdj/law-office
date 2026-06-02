package com.lawoffice.system.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OnlyOfficeHistoryVersionVO {

    private String fileId;

    private String version;

    private String editor;

    private LocalDateTime editTime;

    private String remark;
}
