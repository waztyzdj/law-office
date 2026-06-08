package com.lawoffice.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.framework.entity.BaseTenantEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_process_start_permission")
@ModuleInfo(value = "workflow:start-permission", name = "流程发起权限", description = "审批中心流程发起权限")
@Schema(description = "审批中心流程发起权限")
public class ProcessStartPermission extends BaseTenantEntity {

    private String processModelId;

    private String targetType;

    private String targetId;

    private String status;
}
