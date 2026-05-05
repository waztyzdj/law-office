package com.lawoffice.framework.util;

import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Constructor;
import java.util.List;

/**
 * Bean转换工具类
 */
@Slf4j
public class BeanConvertUtils {

    /**
     * 将源对象转换为目标类型的对象
     * 
     * @param source 源对象
     * @param targetClass 目标类型
     * @return 转换后的对象
     */
    public static <T> T convert(Object source, Class<T> targetClass) {
        if (source == null) {
            return null;
        }
        
        try {
            Constructor<T> constructor = targetClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            T target = constructor.newInstance();
            org.springframework.beans.BeanUtils.copyProperties(source, target);
            return target;
        } catch (Exception e) {
            log.error("Bean转换失败: {} -> {}", source.getClass().getSimpleName(), targetClass.getSimpleName(), e);
            throw new RuntimeException("Bean转换失败", e);
        }
    }

    /**
     * 将实体列表转换为DTO列表
     * 
     * @param entities 实体列表
     * @param dtoClass DTO类型
     * @return DTO列表
     */
    public static <E, D> List<D> convertList(List<E> entities, Class<D> dtoClass) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        
        return entities.stream()
                .map(entity -> convert(entity, dtoClass))
                .toList();
    }
}
