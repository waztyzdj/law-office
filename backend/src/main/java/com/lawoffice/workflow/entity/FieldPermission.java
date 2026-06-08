package com.lawoffice.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.framework.entity.BaseTenantEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_field_permission")
@ModuleInfo(value = "workflow:field-permission", name = "字段权限", description = "审批中心节点字段权限")
@Schema(description = "审批中心节点字段权限")
public class FieldPermission extends BaseTenantEntity {

    private String processModelId;

    private String nodeId;

    private String fieldKey;

    private String permission;

    private Integer requiredFlag;
}
