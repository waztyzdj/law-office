package com.lawoffice.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.framework.entity.BaseTenantEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_attachment")
@ModuleInfo(value = "workflow:attachment", name = "审批附件", description = "审批中心附件业务记录")
@Schema(description = "审批中心附件业务记录")
public class Attachment extends BaseTenantEntity {

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
