package com.lawoffice.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.entity.BaseTenantEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_file_acl")
@Schema(description = "文件访问授权")
public class SysFileAcl extends BaseTenantEntity {

    @Schema(description = "文件ID")
    private String fileId;

    @Schema(description = "授权目标类型(user/depart/role/tenant)")
    private String targetType;

    @Schema(description = "授权目标ID")
    private String targetId;

    @Schema(description = "权限(read/download/update/manage)")
    private String permission;

    @Schema(description = "过期时间")
    private LocalDateTime expireTime;
}
