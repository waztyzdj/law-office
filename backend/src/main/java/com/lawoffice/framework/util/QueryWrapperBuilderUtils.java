package com.lawoffice.framework.util;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

/**
 * MyBatis Plus QueryWrapper 构建工具类
 */
@Slf4j
public class QueryWrapperBuilderUtils {

    /**
     * 需要忽略的参数名（分页和排序参数）
     */
    private static final Set<String> IGNORE_PARAMS = Set.of(
            "pageNum", "pageSize", "current", "size", 
            "orderBy", "order", "_orderBy", "_orderDirection",
            "sortField", "sortOrder", "sort"
    );

    /**
     * 根据查询参数构建 QueryWrapper
     * 
     * @param queryParams 查询参数Map（驼峰命名）
     * @return QueryWrapper 查询条件
     */
    public static <T> QueryWrapper<T> build(Map<String, Object> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return new QueryWrapper<>();
        }

        QueryWrapper<T> wrapper = new QueryWrapper<>();

        for (Map.Entry<String, Object> entry : queryParams.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // 跳过空值
            if (value == null || (value instanceof String && StrUtil.isBlank((String) value))) {
                continue;
            }

            // 跳过分页和排序参数
            if (IGNORE_PARAMS.contains(key)) {
                log.debug("跳过参数: {} = {}", key, value);
                continue;
            }

            // 处理带操作符的字段（如：real_name_like, status_eq）
            if (key.contains("_")) {
                int lastUnderscore = key.lastIndexOf('_');
                String fieldName = key.substring(0, lastUnderscore);
                String operator = key.substring(lastUnderscore + 1);
                
                // 检查是否是有效的操作符
                if (isValidOperator(operator)) {
                    String dbFieldName = camelToSnake(fieldName);
                    applyCondition(wrapper, dbFieldName, operator, value);
                } else {
                    // 不是有效操作符，整个key作为字段名，使用等值查询
                    String dbFieldName = camelToSnake(key);
                    wrapper.eq(dbFieldName, value);
                }
            } else {
                // 普通字段，使用等值查询
                String dbFieldName = camelToSnake(key);
                wrapper.eq(dbFieldName, value);
            }
        }

        return wrapper;
    }

    /**
     * 判断是否是有效的操作符
     */
    private static boolean isValidOperator(String operator) {
        if (StrUtil.isBlank(operator)) {
            return false;
        }
        String lowerOp = operator.toLowerCase();
        return Set.of("like", "eq", "ne", "gt", "ge", "lt", "le", "in", "between", "like_start", "like_end").contains(lowerOp);
    }

    /**
     * 应用排序（单独的方法，由 Controller 调用）
     */
    public static <T> void applyOrderBy(QueryWrapper<T> wrapper, Object orderByValue) {
        if (orderByValue == null) {
            return;
        }
        
        if (orderByValue instanceof String) {
            String orderBy = (String) orderByValue;
            if (orderBy.startsWith("-")) {
                String field = camelToSnake(orderBy.substring(1));
                wrapper.orderByDesc(field);
            } else {
                String field = camelToSnake(orderBy);
                wrapper.orderByAsc(field);
            }
        } else if (orderByValue instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, String> orderMap = (Map<String, String>) orderByValue;
            for (Map.Entry<String, String> entry : orderMap.entrySet()) {
                String field = camelToSnake(entry.getKey());
                String direction = entry.getValue();
                if ("desc".equalsIgnoreCase(direction)) {
                    wrapper.orderByDesc(field);
                } else {
                    wrapper.orderByAsc(field);
                }
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
            case "like_start":
                // 开头是：field LIKE 'value%'
                wrapper.likeRight(field, value);
                break;
            case "like_end":
                // 结尾是：field LIKE '%value'
                wrapper.likeLeft(field, value);
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
                } else if (value instanceof java.util.Collection) {
                    wrapper.in(field, (java.util.Collection<?>) value);
                }
                break;
            case "between":
                if (value instanceof String) {
                    String[] values = ((String) value).split(",");
                    if (values.length == 2) {
                        wrapper.between(field, values[0], values[1]);
                    }
                } else if (value instanceof ArrayList) {
                    ArrayList<String> values = (ArrayList<String>) value;
                    if (values.size() == 2) {
                        wrapper.between(field, values.get(0), values.get(1));
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
