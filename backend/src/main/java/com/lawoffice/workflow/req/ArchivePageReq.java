package com.lawoffice.workflow.req;

import com.lawoffice.framework.req.BasePageReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ArchivePageReq extends BasePageReq {

    private String instanceTitle;

    private String instanceNo;

    private String categoryId;

    private String processKey;

    private String processName;

    private Integer processVersion;

    private String starterRealname;

    private String instanceStatus;

    private String archiveSource;

    private String processStartTimeGe;

    private String processStartTimeLe;

    private String processEndTimeGe;

    private String processEndTimeLe;

    private String archiveTimeGe;

    private String archiveTimeLe;

    private String archiveReason;
}
