package com.lawoffice.home.req;

import com.lawoffice.framework.req.BasePageReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WorkbenchQuickEntryPageReq extends BasePageReq {
    private String entryCode;
    private String entryName;
    private String entryType;
    private String status;
}
