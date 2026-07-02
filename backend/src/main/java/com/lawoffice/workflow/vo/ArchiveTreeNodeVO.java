package com.lawoffice.workflow.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ArchiveTreeNodeVO {

    private String key;

    private String title;

    private String type;

    private String categoryId;

    private String processModelId;

    private String processKey;

    private String processName;

    private List<ArchiveTreeNodeVO> children = new ArrayList<>();
}
