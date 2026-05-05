package com.lawoffice.framework.util;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.write.style.column.LongestMatchColumnWidthStyleStrategy;
import com.lawoffice.framework.annotation.ModuleInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Excel工具类
 * 提供导入和导出功能
 */
@Slf4j
public class ExcelUtils {

    /**
     * 读取Excel表头（第一行）
     */
    public static Map<Integer, String> readHeader(MultipartFile file) {
        try {
            List<Map<Integer, String>> allData = EasyExcel.read(file.getInputStream())
                    .sheet()
                    .headRowNumber(0)
                    .doReadSync();
            
            return allData.isEmpty() ? new HashMap<>() : allData.get(0);
        } catch (Exception e) {
            log.error("读取Excel表头失败", e);
            throw new RuntimeException("读取Excel表头失败: " + e.getMessage());
        }
    }

    /**
     * 读取Excel数据（跳过表头）
     */
    public static List<Map<Integer, String>> readData(MultipartFile file) {
        try {
            return EasyExcel.read(file.getInputStream())
                    .sheet()
                    .headRowNumber(1)
                    .doReadSync();
        } catch (Exception e) {
            log.error("读取Excel数据失败", e);
            throw new RuntimeException("读取Excel数据失败: " + e.getMessage());
        }
    }

    /**
     * 将Map数据列表转换为实体列表
     */
    public static <T> List<T> convertToEntities(List<Map<Integer, String>> dataList, 
                                                  Map<Integer, String> headMap, 
                                                  Class<T> clazz) {
        List<T> result = new ArrayList<>();
        
        if (dataList == null || dataList.isEmpty()) {
            return result;
        }
        
        Map<Integer, java.lang.reflect.Field> columnFieldMap = buildColumnFieldMap(headMap, clazz);
        
        for (Map<Integer, String> row : dataList) {
            try {
                T entity = createEntityFromRow(row, columnFieldMap, clazz);
                if (entity != null) {
                    result.add(entity);
                }
            } catch (Exception e) {
                log.error("转换行数据失败，跳过该行", e);
            }
        }
        
        return result;
    }

    /**
     * 导出数据为Excel
     */
    public static <T> void export(HttpServletResponse response, List<T> data, Class<T> clazz) {
        try {
            String fileName = getExportFileName(clazz);
            
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            response.setHeader("Content-Disposition", 
                    "attachment;filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8) + ".xlsx");
            response.setHeader("Access-Control-Expose-Headers", "Content-Disposition");
            
            Set<String> excludedFields = getExcludedFields(clazz);
            
            EasyExcel.write(response.getOutputStream(), clazz)
                    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
                    .excludeColumnFieldNames(excludedFields)
                    .sheet("数据")
                    .doWrite(data);
            
            response.getOutputStream().flush();
        } catch (IOException e) {
            log.error("导出Excel失败", e);
            throw new RuntimeException("导出Excel失败: " + e.getMessage());
        }
    }

    /**
     * 构建列索引到字段的映射
     */
    private static <T> Map<Integer, java.lang.reflect.Field> buildColumnFieldMap(Map<Integer, String> headMap, Class<T> clazz) {
        Map<Integer, java.lang.reflect.Field> columnFieldMap = new HashMap<>();
        Map<String, java.lang.reflect.Field> fieldLookupMap = buildFieldLookupMap(clazz);
        
        for (Map.Entry<Integer, String> entry : headMap.entrySet()) {
            String headerName = entry.getValue();
            if (headerName == null || headerName.trim().isEmpty()) {
                continue;
            }
            
            java.lang.reflect.Field field = matchField(headerName, fieldLookupMap);
            if (field != null) {
                columnFieldMap.put(entry.getKey(), field);
            } else {
                log.warn("列[{}]未匹配到任何字段", headerName);
            }
        }
        
        return columnFieldMap;
    }

    /**
     * 构建字段查找表
     */
    private static <T> Map<String, java.lang.reflect.Field> buildFieldLookupMap(Class<T> clazz) {
        Map<String, java.lang.reflect.Field> lookupMap = new HashMap<>();
        
        getAllFields(clazz).forEach(field -> {
            field.setAccessible(true);
            
            String fieldNameLower = field.getName().toLowerCase();
            lookupMap.put(fieldNameLower, field);
            
            if (field.isAnnotationPresent(ExcelProperty.class)) {
                ExcelProperty excelProperty = field.getAnnotation(ExcelProperty.class);
                for (String name : excelProperty.value()) {
                    lookupMap.put(name.toLowerCase(), field);
                }
            }
        });
        
        return lookupMap;
    }

    /**
     * 匹配字段
     */
    private static java.lang.reflect.Field matchField(String headerName, Map<String, java.lang.reflect.Field> lookupMap) {
        String headerLower = headerName.toLowerCase().trim();
        
        java.lang.reflect.Field field = lookupMap.get(headerLower);
        
        if (field == null) {
            String normalizedHeader = normalizeFieldName(headerLower);
            for (Map.Entry<String, java.lang.reflect.Field> entry : lookupMap.entrySet()) {
                if (normalizeFieldName(entry.getKey()).equals(normalizedHeader)) {
                    field = entry.getValue();
                    break;
                }
            }
        }
        
        return field;
    }

    /**
     * 从单行数据创建实体对象
     */
    private static <T> T createEntityFromRow(Map<Integer, String> row, 
                                               Map<Integer, java.lang.reflect.Field> columnFieldMap, 
                                               Class<T> clazz) {
        try {
            T entity = clazz.getDeclaredConstructor().newInstance();
            
            for (Map.Entry<Integer, java.lang.reflect.Field> entry : columnFieldMap.entrySet()) {
                String value = row.get(entry.getKey());
                if (value != null && !value.trim().isEmpty()) {
                    setFieldValue(entity, entry.getValue(), value.trim());
                }
            }
            
            return entity;
        } catch (Exception e) {
            log.error("创建实体对象失败", e);
            return null;
        }
    }

    /**
     * 获取类的所有字段（包括父类）
     */
    private static List<java.lang.reflect.Field> getAllFields(Class<?> clazz) {
        List<java.lang.reflect.Field> fields = new ArrayList<>();
        
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            for (java.lang.reflect.Field field : currentClass.getDeclaredFields()) {
                fields.add(field);
            }
            currentClass = currentClass.getSuperclass();
        }
        
        return fields;
    }

    /**
     * 标准化字段名
     */
    private static String normalizeFieldName(String fieldName) {
        if (fieldName == null) {
            return "";
        }
        return fieldName.toLowerCase()
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "")
                .trim();
    }

    /**
     * 设置字段值
     */
    private static void setFieldValue(Object entity, java.lang.reflect.Field field, String value) {
        try {
            Class<?> fieldType = field.getType();
            
            if (fieldType.equals(String.class)) {
                field.set(entity, value);
            } else if (fieldType.equals(Integer.class) || fieldType.equals(int.class)) {
                field.set(entity, Integer.parseInt(value));
            } else if (fieldType.equals(Long.class) || fieldType.equals(long.class)) {
                field.set(entity, Long.parseLong(value));
            } else if (fieldType.equals(Double.class) || fieldType.equals(double.class)) {
                field.set(entity, Double.parseDouble(value));
            } else if (fieldType.equals(Float.class) || fieldType.equals(float.class)) {
                field.set(entity, Float.parseFloat(value));
            } else if (fieldType.equals(Boolean.class) || fieldType.equals(boolean.class)) {
                field.set(entity, Boolean.parseBoolean(value));
            } else {
                field.set(entity, value);
            }
        } catch (Exception e) {
            log.debug("字段[{}]设置值[{}]失败: {}", field.getName(), value, e.getMessage());
        }
    }

    /**
     * 获取需要排除的字段列表（没有 @ExcelProperty 注解的字段）
     */
    private static <T> Set<String> getExcludedFields(Class<T> clazz) {
        Set<String> excludedFields = new HashSet<>();
        
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            for (java.lang.reflect.Field field : currentClass.getDeclaredFields()) {
                if (!field.isAnnotationPresent(ExcelProperty.class)) {
                    excludedFields.add(field.getName());
                }
            }
            currentClass = currentClass.getSuperclass();
        }
        
        return excludedFields;
    }

    /**
     * 获取导出文件名
     */
    private static <T> String getExportFileName(Class<T> clazz) {
        Schema schema = clazz.getAnnotation(Schema.class);
        if (schema != null && !schema.description().isEmpty()) {
            return schema.description();
        }
        
        ModuleInfo moduleInfo = clazz.getAnnotation(ModuleInfo.class);
        if (moduleInfo != null && !moduleInfo.name().isEmpty()) {
            return moduleInfo.name();
        }
        
        return "export";
    }
}
