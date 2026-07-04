package com.lawoffice.home.req;

import com.lawoffice.framework.req.BasePageReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WorkbenchRecentPageReq extends BasePageReq {
    private String recordType;
}
