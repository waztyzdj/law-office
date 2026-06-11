package com.lawoffice.system.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 部门成员组织关系。
 */
@Data
@Schema(description = "部门成员组织关系")
public class DepartMemberRelationVO {

    @Schema(description = "部门ID")
    private String departId;

    @Schema(description = "用户ID")
    private String userId;

    @Schema(description = "账号")
    private String username;

    @Schema(description = "姓名")
    private String realname;

    @Schema(description = "是否主部门：0-否，1-是")
    private Integer primaryDepartFlag;

    @Schema(description = "是否部门负责人：0-否，1-是")
    private Integer departLeaderFlag;

    @Schema(description = "直属上级用户ID")
    private String supervisorUserId;

    @Schema(description = "直属上级账号")
    private String supervisorUsername;

    @Schema(description = "直属上级姓名")
    private String supervisorRealname;
}
