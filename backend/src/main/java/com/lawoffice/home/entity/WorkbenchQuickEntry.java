package com.lawoffice.home.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.framework.entity.BaseTenantEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("home_workbench_quick_entry")
@ModuleInfo(value = "home:quick-entry", name = "工作台快捷菜单", description = "工作台快捷菜单")
@Schema(description = "工作台快捷菜单")
public class WorkbenchQuickEntry extends BaseTenantEntity {

    private String ownerType;

    private String ownerUserId;

    private String entryCode;

    private String entryName;

    private String entryType;

    private String menuId;

    private String path;

    private String permissionCode;

    private String icon;

    private Integer sortNo;

    private String status;

    private String configJson;
}
