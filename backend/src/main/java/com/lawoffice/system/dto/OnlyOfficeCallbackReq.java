package com.lawoffice.system.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OnlyOfficeCallbackReq {

    private String changesUrl;

    private String error;

    private Map<String, Object> history;

    private String key;

    private Integer status;

    private String url;

    private List<String> users;
}
