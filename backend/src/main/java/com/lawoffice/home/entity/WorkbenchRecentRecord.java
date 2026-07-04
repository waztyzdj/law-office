package com.lawoffice.home.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.framework.entity.BaseTenantEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("home_workbench_recent_record")
@ModuleInfo(value = "home:recent", name = "工作台近期工作", description = "工作台近期工作记录")
@Schema(description = "工作台近期工作记录")
public class WorkbenchRecentRecord extends BaseTenantEntity {

    private String userId;

    private String recordType;

    private String recordKey;

    private String moduleCode;

    private String bizId;

    private String title;

    private String targetType;

    private String targetPath;

    private String targetParamsJson;

    private LocalDateTime sourceTime;

    private LocalDateTime lastVisitTime;

    private Integer visitCount;
}
