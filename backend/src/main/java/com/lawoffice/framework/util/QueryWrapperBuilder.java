package com.lawoffice.framework.util;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * MyBatis Plus QueryWrapper 构建工具类
 */
@Slf4j
public class QueryWrapperBuilder {

    /**
     * 根据查询参数构建 QueryWrapper
     */
    public static <T> QueryWrapper<T> build(Map<String, Object> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return new QueryWrapper<>();
        }

        QueryWrapper<T> wrapper = new QueryWrapper<>();

        for (Map.Entry<String, Object> entry : queryParams.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value == null || (value instanceof String && StrUtil.isBlank((String) value))) {
                continue;
            }

            if ("orderBy".equals(key)) {
                applyOrderBy(wrapper, value);
                continue;
            }

            if (key.contains("_")) {
                int lastUnderscore = key.lastIndexOf('_');
                String fieldName = key.substring(0, lastUnderscore);
                String operator = key.substring(lastUnderscore + 1);
                
                String dbFieldName = camelToSnake(fieldName);
                
                applyCondition(wrapper, dbFieldName, operator, value);
            } else {
                String dbFieldName = camelToSnake(key);
                wrapper.eq(dbFieldName, value);
            }
        }

        return wrapper;
    }

    /**
     * 应用排序
     */
    private static <T> void applyOrderBy(QueryWrapper<T> wrapper, Object orderByValue) {
        if (orderByValue instanceof String) {
            String orderBy = (String) orderByValue;
            if (orderBy.startsWith("-")) {
                String field = camelToSnake(orderBy.substring(1));
                wrapper.orderByDesc(field);
            } else {
                String field = camelToSnake(orderBy);
                wrapper.orderByAsc(field);
            }
        }
    }

    /**
     * 应用查询条件
     */
    @SuppressWarnings("unchecked")
    private static <T> void applyCondition(QueryWrapper<T> wrapper, String field, String operator, Object value) {
        switch (operator.toLowerCase()) {
            case "like":
                wrapper.like(field, value);
                break;
            case "eq":
                wrapper.eq(field, value);
                break;
            case "ne":
                wrapper.ne(field, value);
                break;
            case "gt":
                wrapper.gt(field, value);
                break;
            case "ge":
                wrapper.ge(field, value);
                break;
            case "lt":
                wrapper.lt(field, value);
                break;
            case "le":
                wrapper.le(field, value);
                break;
            case "in":
                if (value instanceof String) {
                    String[] values = ((String) value).split(",");
                    wrapper.in(field, (Object[]) values);
                }
                break;
            case "between":
                if (value instanceof String) {
                    String[] values = ((String) value).split(",");
                    if (values.length == 2) {
                        wrapper.between(field, values[0], values[1]);
                    }
                }
                break;
            default:
                wrapper.eq(field, value);
                break;
        }
    }

    /**
     * 驼峰转蛇形命名
     */
    private static String camelToSnake(String camelCase) {
        if (StrUtil.isBlank(camelCase)) {
            return camelCase;
        }
        return StrUtil.toUnderlineCase(camelCase);
    }
}
