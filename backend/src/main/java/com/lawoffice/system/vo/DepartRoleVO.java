package com.lawoffice.system.vo;

import com.lawoffice.framework.vo.BaseVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "部门角色")
public class DepartRoleVO extends BaseVO {

    @Schema(description = "部门id")
    private String departId;

    @Schema(description = "部门角色名称")
    private String roleName;

    @Schema(description = "部门角色编码")
    private String roleCode;

    @Schema(description = "是否可作为审批岗位：0-否，1-是")
    private Integer workflowEnabled;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "是否部门默认角色")
    private Boolean defaultRole;
}
