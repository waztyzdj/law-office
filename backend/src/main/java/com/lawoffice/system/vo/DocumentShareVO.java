package com.lawoffice.system.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lawoffice.framework.vo.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class DocumentShareVO extends BaseVO {

    private String fileId;

    private String targetType;

    private String targetId;

    private String targetName;

    private String permission;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime expireTime;
}
