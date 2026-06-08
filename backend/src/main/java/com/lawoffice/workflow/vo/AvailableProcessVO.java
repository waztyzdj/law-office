package com.lawoffice.workflow.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lawoffice.framework.vo.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class AvailableProcessVO extends BaseVO {

    private String categoryId;

    private String processKey;

    private String processName;

    private Integer processVersion;

    private String designerType;

    private String formDefinitionId;

    private String formKey;

    private String formName;

    private Integer formVersion;

    private String startScopeType;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime publishedTime;
}
