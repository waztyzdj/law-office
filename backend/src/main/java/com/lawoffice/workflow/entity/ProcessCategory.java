package com.lawoffice.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.framework.entity.BaseTenantEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_process_category")
@ModuleInfo(value = "workflow:category", name = "流程分类", description = "审批中心流程分类")
@Schema(description = "审批中心流程分类")
public class ProcessCategory extends BaseTenantEntity {

    private String parentId;

    private String categoryCode;

    private String categoryName;

    private Integer sortOrder;

    private String status;

    private String remark;
}
