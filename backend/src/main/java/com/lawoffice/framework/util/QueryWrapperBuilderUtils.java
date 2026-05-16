package com.lawoffice.framework.util;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lawoffice.framework.req.BaseQueryReq;
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
     * 根据 BaseQueryReq 构建 QueryWrapper（包含查询条件和排序）
     * 
     * @param queryReq 查询请求对象
     * @return QueryWrapper 查询条件
     */
    public static <T> QueryWrapper<T> build(BaseQueryReq queryReq) {
        if (queryReq == null) {
            return new QueryWrapper<>();
        }

        Map<String, Object> queryParams = queryReq.getQueryParams();
        QueryWrapper<T> wrapper = new QueryWrapper<>();

        // 构建查询条件
        if (queryParams != null && !queryParams.isEmpty()) {
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
        }

        // 应用排序
        applyMultiSorting(wrapper, queryReq.getSortField(), queryReq.getSortOrder());

        return wrapper;
    }

    /**
     * 应用多字段排序条件（支持逗号分隔的多个字段）
     * sortField 格式："createTime,updateTime"
     * sortOrder 格式："desc,asc" （与字段一一对应，如果只有一个则所有字段都用该排序方向）
     */
    private static <T> void applyMultiSorting(QueryWrapper<T> wrapper, String sortField, String sortOrder) {
        if (StrUtil.isBlank(sortField)) {
            return;
        }

        // 分割排序字段
        String[] fields = sortField.split(",");
        
        // 分割排序方向
        String[] orders = sortOrder != null ? sortOrder.split(",") : new String[]{"desc"};

        for (int i = 0; i < fields.length; i++) {
            String field = fields[i].trim();
            if (StrUtil.isBlank(field)) {
                continue;
            }

            // 获取对应的排序方向，如果只有一个排序方向则所有字段都用它
            String order = i < orders.length ? orders[i].trim() : orders[orders.length - 1].trim();
            
            // 将驼峰命名转换为蛇形命名
            String dbFieldName = camelToSnake(field);
            
            // 应用排序
            if ("asc".equalsIgnoreCase(order)) {
                wrapper.orderByAsc(dbFieldName);
            } else {
                wrapper.orderByDesc(dbFieldName);
            }
        }
    }

    /**
     * 判断是否是有效的操作符
     */
    private static boolean isValidOperator(String operator) {
        if (StrUtil.isBlank(operator)) {
            return false;
        }
        String lowerOp = operator.toLowerCase();
        return Set.of("like", "eq", "ne", "gt", "ge", "lt", "le", "in", "between", "likestart", "likeend").contains(lowerOp);
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
            case "likestart":
                // 开头是：field LIKE 'value%'
                wrapper.likeRight(field, value);
                break;
            case "likeend":
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
