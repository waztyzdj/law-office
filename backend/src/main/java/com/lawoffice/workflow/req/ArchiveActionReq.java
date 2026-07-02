package com.lawoffice.workflow.req;

import com.lawoffice.framework.req.BaseReq;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class ArchiveActionReq extends BaseReq {

    private String processInstanceId;

    private List<String> processInstanceIds;

    @Size(max = 500, message = "归档说明不能超过500个字符")
    private String archiveReason;
}
