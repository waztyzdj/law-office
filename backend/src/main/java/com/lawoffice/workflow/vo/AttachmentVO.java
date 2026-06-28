package com.lawoffice.workflow.vo;

import com.lawoffice.framework.vo.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AttachmentVO extends BaseVO {
    private String tenantId;
    private String processInstanceId;
    private String taskId;
    private String approvalRecordId;
    private String nodeId;
    private String nodeName;
    private String fileId;
    private String fileRelationId;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String attachmentSource;
    private String uploaderUserId;
    private String uploaderUsername;
    private String uploaderRealname;
    private String status;
    private Integer sortOrder;
    private String remark;
}
