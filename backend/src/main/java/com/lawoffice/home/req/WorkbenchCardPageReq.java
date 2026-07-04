package com.lawoffice.home.req;

import com.lawoffice.framework.req.BasePageReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WorkbenchCardPageReq extends BasePageReq {
    private String cardCode;
    private String cardName;
    private String componentKey;
    private String status;
}
