package com.lawoffice.message.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.entity.BaseTenantEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_message_action")
@Schema(description = "站内消息动作")
public class SysMessageAction extends BaseTenantEntity {

    private String messageId;

    private Integer actionType;

    private String actionName;

    private String routePath;

    private String routeQuery;

    private String externalUrl;

    private String bizType;

    private String bizId;

    private Integer openType;

    private Integer sortOrder;
}
