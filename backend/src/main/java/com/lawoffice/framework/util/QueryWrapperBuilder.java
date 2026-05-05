package com.lawoffice.framework.util;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lawoffice.framework.util.StringConvertUtils;

import java.util.List;
import java.util.Map;

/**
 * QueryWrapper构建工具类
 * 支持从Map参数自动构建查询条件
 */
public class QueryWrapperBuilder {

    /**
     * 根据queryParams构建QueryWrapper
     * 支持的操作符后缀：_like, _eq, _ne, _gt, _ge, _lt, _le, _in, _between
     * 
     * @param queryParams 查询参数Map（驼峰命名）
     * @param <T> 实体类型
     * @return QueryWrapper
     */
    public static <T> QueryWrapper<T> build(Map<String, Object> queryParams) {
        QueryWrapper<T> wrapper = new QueryWrapper<>();
        
        if (queryParams == null || queryParams.isEmpty()) {
            return wrapper;
        }
        
        String orderByField = null;
        String orderDirection = null;
        
        for (Map.Entry<String, Object> entry : queryParams.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (value == null || (value instanceof String && ((String) value).isEmpty())) {
                continue;
            }
            
            if ("_orderBy".equals(key)) {
                orderByField = value.toString();
                continue;
            }
            
            if ("_orderDirection".equals(key)) {
                orderDirection = value.toString();
                continue;
            }
            
            applyCondition(wrapper, key, value);
        }
        
        applyOrderBy(wrapper, orderByField, orderDirection);
        
        return wrapper;
    }

    /**
     * 应用单个查询条件
     */
    @SuppressWarnings("unchecked")
    private static <T> void applyCondition(QueryWrapper<T> wrapper, String key, Object value) {
        if (key.endsWith("_like")) {
            String field = StringConvertUtils.camelToSnake(key.substring(0, key.length() - 5));
            wrapper.like(field, value);
        } else if (key.endsWith("_eq")) {
            String field = StringConvertUtils.camelToSnake(key.substring(0, key.length() - 3));
            wrapper.eq(field, value);
        } else if (key.endsWith("_ne")) {
            String field = StringConvertUtils.camelToSnake(key.substring(0, key.length() - 3));
            wrapper.ne(field, value);
        } else if (key.endsWith("_gt")) {
            String field = StringConvertUtils.camelToSnake(key.substring(0, key.length() - 3));
            wrapper.gt(field, value);
        } else if (key.endsWith("_ge")) {
            String field = StringConvertUtils.camelToSnake(key.substring(0, key.length() - 3));
            wrapper.ge(field, value);
        } else if (key.endsWith("_lt")) {
            String field = StringConvertUtils.camelToSnake(key.substring(0, key.length() - 3));
            wrapper.lt(field, value);
        } else if (key.endsWith("_le")) {
            String field = StringConvertUtils.camelToSnake(key.substring(0, key.length() - 3));
            wrapper.le(field, value);
        } else if (key.endsWith("_in")) {
            String field = StringConvertUtils.camelToSnake(key.substring(0, key.length() - 3));
            if (value instanceof List) {
                wrapper.in(field, (List<?>) value);
            }
        } else if (key.endsWith("_between")) {
            String field = StringConvertUtils.camelToSnake(key.substring(0, key.length() - 8));
            if (value instanceof List && ((List<?>) value).size() == 2) {
                List<?> values = (List<?>) value;
                wrapper.between(field, values.get(0), values.get(1));
            }
        }
    }

    /**
     * 应用排序条件
     */
    public static <T> void applyOrderBy(QueryWrapper<T> wrapper, String orderByField, String orderDirection) {
        if (orderByField == null || orderByField.isEmpty()) {
            return;
        }
        
        String[] fields = orderByField.split(",");
        String[] directions = orderDirection != null ? orderDirection.split(",") : new String[]{"ASC"};
        
        for (int i = 0; i < fields.length; i++) {
            String field = StringConvertUtils.camelToSnake(fields[i].trim());
            String direction = i < directions.length ? directions[i].trim().toUpperCase() : "ASC";
            
            if (!"ASC".equals(direction) && !"DESC".equals(direction)) {
                direction = "ASC";
            }
            
            if ("DESC".equals(direction)) {
                wrapper.orderByDesc(field);
            } else {
                wrapper.orderByAsc(field);
            }
        }
    }
}
