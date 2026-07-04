package com.lawoffice.home.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WorkbenchLayoutVO {
    private List<WorkbenchLayoutCardVO> cards = new ArrayList<>();
    private List<WorkbenchLayoutCardVO> hiddenCards = new ArrayList<>();
}
