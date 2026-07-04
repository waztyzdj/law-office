package com.lawoffice.home.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class WorkbenchCardDataVO {
    private String cardCode;
    private Map<String, Object> summary = new HashMap<>();
    private List<Map<String, Object>> items = new ArrayList<>();
}
