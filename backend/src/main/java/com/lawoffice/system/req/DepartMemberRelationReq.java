package com.lawoffice.system.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 部门成员组织关系保存请求。
 */
@Data
public class DepartMemberRelationReq implements Serializable {
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "部门ID不能为空")
    private String departId;

    @Valid
    @NotEmpty(message = "成员关系不能为空")
    private List<MemberRelation> members = new ArrayList<>();

    @Data
    public static class MemberRelation implements Serializable {
        private static final long serialVersionUID = 1L;

        @NotBlank(message = "用户ID不能为空")
        private String userId;

        private Integer primaryDepartFlag;

        private Integer departLeaderFlag;

        private String supervisorUserId;
    }
}
