package com.lawoffice.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.entity.BaseTenantEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_file_relation")
@Schema(description = "文件业务关联")
public class SysFileRelation extends BaseTenantEntity {

    private String fileId;

    private String bizType;

    private String bizId;

    private Integer relationType;

    private Integer sortOrder;
}
