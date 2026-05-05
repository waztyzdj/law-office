package com.lawoffice.framework.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class BasePageDTO<T> extends BaseDTO<T> {
    
    private Integer pageNum = 1;
    
    private Integer pageSize = 10;
}
