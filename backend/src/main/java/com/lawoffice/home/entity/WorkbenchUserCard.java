package com.lawoffice.home.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.framework.entity.BaseTenantEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("home_workbench_user_card")
@ModuleInfo(value = "home:user-card", name = "工作台用户布局", description = "工作台用户卡片布局")
@Schema(description = "工作台用户卡片布局")
public class WorkbenchUserCard extends BaseTenantEntity {

    private String userId;

    private String cardCode;

    private Integer visible;

    private Integer sortNo;

    private String size;

    private Integer gridX;

    private Integer gridY;

    private Integer gridW;

    private Integer gridH;

    private String configJson;
}
