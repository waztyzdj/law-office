package com.lawoffice.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.framework.entity.BaseTenantEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_archive_record")
@ModuleInfo(value = "workflow:archive", name = "流程归档", description = "审批中心流程归档记录")
@Schema(description = "审批中心流程归档记录")
public class ArchiveRecord extends BaseTenantEntity {

    private String processInstanceId;

    private String processModelId;

    private String categoryId;

    private String categoryName;

    private String processKey;

    private String processName;

    private Integer processVersion;

    private String formInstanceId;

    private String formDefinitionId;

    private String instanceNo;

    private String instanceTitle;

    private String starterUserId;

    private String starterUsername;

    private String starterRealname;

    private String instanceStatus;

    private LocalDateTime processStartTime;

    private LocalDateTime processEndTime;

    private String archiveSource;

    private String archiveReason;

    private String archiverUserId;

    private String archiverUsername;

    private String archiverRealname;

    private LocalDateTime archiveTime;
}
