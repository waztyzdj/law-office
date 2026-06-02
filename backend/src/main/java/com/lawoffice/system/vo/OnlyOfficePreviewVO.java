package com.lawoffice.system.vo;

import lombok.Data;

import java.util.Map;

@Data
public class OnlyOfficePreviewVO {

    private String documentServerApiUrl;

    private Map<String, Object> config;
}
