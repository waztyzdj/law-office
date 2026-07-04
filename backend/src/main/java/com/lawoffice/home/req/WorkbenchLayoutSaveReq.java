package com.lawoffice.home.req;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class WorkbenchLayoutSaveReq {
    @Valid
    @NotEmpty(message = "卡片布局不能为空")
    private List<Card> cards;

    @Data
    public static class Card {
        @NotBlank(message = "卡片编码不能为空")
        private String cardCode;
        private Boolean visible;
        private String size;
        private Integer gridX;
        private Integer gridY;
        private Integer gridW;
        private Integer gridH;
        private String configJson;
        private JsonNode config;
    }
}
