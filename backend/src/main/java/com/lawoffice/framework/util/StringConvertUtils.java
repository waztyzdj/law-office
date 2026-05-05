package com.lawoffice.framework.util;

/**
 * 字符串转换工具类
 */
public class StringConvertUtils {

    /**
     * 将驼峰命名转换为蛇形命名
     * 例如：userName -> user_name
     * 
     * @param camelCase 驼峰命名字符串
     * @return 蛇形命名字符串
     */
    public static String camelToSnake(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) {
            return camelCase;
        }
        
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    result.append('_');
                }
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * 将蛇形命名转换为驼峰命名
     * 例如：user_name -> userName
     * 
     * @param snakeCase 蛇形命名字符串
     * @return 驼峰命名字符串
     */
    public static String snakeToCamel(String snakeCase) {
        if (snakeCase == null || snakeCase.isEmpty()) {
            return snakeCase;
        }
        
        StringBuilder result = new StringBuilder();
        boolean nextUpper = false;
        
        for (char c : snakeCase.toCharArray()) {
            if (c == '_') {
                nextUpper = true;
            } else {
                if (nextUpper) {
                    result.append(Character.toUpperCase(c));
                    nextUpper = false;
                } else {
                    result.append(c);
                }
            }
        }
        
        return result.toString();
    }
}
