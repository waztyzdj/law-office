package com.lawoffice.home.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WorkbenchQuickEntryListVO {
    private List<WorkbenchQuickEntryVO> entries = new ArrayList<>();
}
