package com.lawoffice.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码生成工具（仅用于初始化数据）
 */
public class PasswordGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        
        // 生成admin123的BCrypt哈希
        String adminPassword = "admin123";
        String adminHash = encoder.encode(adminPassword);
        System.out.println("admin123 的 BCrypt 哈希:");
        System.out.println(adminHash);
        System.out.println();
        
        // 验证
        boolean matches = encoder.matches(adminPassword, adminHash);
        System.out.println("验证结果: " + matches);
        System.out.println();
        
        // 生成123456的BCrypt哈希
        String userPassword = "123456";
        String userHash = encoder.encode(userPassword);
        System.out.println("123456 的 BCrypt 哈希:");
        System.out.println(userHash);
        System.out.println();
        
        // 验证
        boolean matches2 = encoder.matches(userPassword, userHash);
        System.out.println("验证结果: " + matches2);
    }
}
