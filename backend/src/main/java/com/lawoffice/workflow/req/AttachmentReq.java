package com.lawoffice.workflow.req;

import com.lawoffice.framework.req.BaseReq;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AttachmentReq extends BaseReq {
    private String processInstanceId;
    private String taskId;
    private String approvalRecordId;
    private String nodeId;
    private String nodeName;
    private String fileId;
    private String fileRelationId;
    private String attachmentSource;
    private String uploaderUserId;
    private String uploaderUsername;
    private String uploaderRealname;
    private String status;
    private Integer sortOrder;
    private String remark;
}
