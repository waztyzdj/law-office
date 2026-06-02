package com.lawoffice.system.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "onlyoffice")
public class OnlyOfficeProperties {

    private Boolean enabled = false;

    private String documentServerUrl;

    private String serverBaseUrl;

    private String jwtSecret;

    private Integer previewTokenMinutes = 10;

    private Integer callbackTokenMinutes = 1440;

    private String renderVersion = "default";
}
