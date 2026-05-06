package com.lawoffice.framework.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class BaseEntity implements Serializable {
    
    private String id;
    
    private LocalDateTime createTime;
    
    private String createBy;
    
    private LocalDateTime updateTime;
    
    private String updateBy;
    
    private Integer deleteFlag;
    
    private LocalDateTime deleteTime;
    
    private String deleteBy;
}
