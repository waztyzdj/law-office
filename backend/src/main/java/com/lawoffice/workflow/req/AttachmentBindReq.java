package com.lawoffice.workflow.req;

import com.lawoffice.framework.req.BaseReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AttachmentBindReq extends BaseReq {

    private String processInstanceId;

    private String taskId;

    private String nodeId;

    private String nodeName;

    private String fileId;

    private String attachmentSource;

    private String remark;
}
