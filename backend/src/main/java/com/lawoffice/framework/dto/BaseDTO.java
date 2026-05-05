package com.lawoffice.framework.dto;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;

import java.util.List;

@Data
public class BaseDTO<T> {
    
    private T entity;
    
    private String id;

    private List<String> deleteIds;
    
    private List<T> entityList;
    
    private RequestContext context;
    
    private HttpServletRequest request;
    
    private HttpServletResponse response;
    
    private QueryWrapper<?> queryWrapper;
}
