package com.lawoffice.workflow.req;

import com.lawoffice.framework.req.BasePageReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AvailableProcessPageReq extends BasePageReq {

    private String categoryId;

    private String processName;

    private String processKey;

    private Integer processVersion;

    private String formName;

    private Integer formVersion;

    private String designerType;

    private String startScopeType;

    private String publishedTimeGe;

    private String publishedTimeLe;
}
