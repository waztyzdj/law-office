package com.lawoffice.home.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.framework.entity.BaseTenantEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("home_workbench_card")
@ModuleInfo(value = "home:card", name = "工作台卡片", description = "工作台卡片配置")
@Schema(description = "工作台卡片配置")
public class WorkbenchCard extends BaseTenantEntity {

    private String cardCode;

    private String cardName;

    private String componentKey;

    private String permissionCode;

    private String status;

    private Integer defaultVisible;

    private Integer defaultSort;

    private String defaultSize;

    private Integer defaultRefreshInterval;

    private String configJson;

    private String remark;
}
