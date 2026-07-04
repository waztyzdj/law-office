package com.lawoffice.home.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class WorkbenchCardSortReq {
    @Valid
    @NotEmpty(message = "排序项不能为空")
    private List<Item> items;

    @Data
    public static class Item {
        @NotBlank(message = "卡片ID不能为空")
        private String id;
        private Integer defaultSort;
    }
}
