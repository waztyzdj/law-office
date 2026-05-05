package com.lawoffice;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.lawoffice.*.mapper")
public class LawOfficeApplication {

    public static void main(String[] args) {
        SpringApplication.run(LawOfficeApplication.class, args);
    }

}
