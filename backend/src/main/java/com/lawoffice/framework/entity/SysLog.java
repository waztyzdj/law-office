package com.lawoffice.framework.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_log")
public class SysLog extends BaseEntity {
    
    private Integer logType;
    
    private String logContent;
    
    private Integer operateType;
    
    private String userid;
    
    private String username;
    
    private String ip;
    
    private String method;
    
    private String requestUrl;
    
    private String requestParam;
    
    private String requestType;
    
    private Long costTime;
    
    private String clientType;
}
