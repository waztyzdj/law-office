package com.lawoffice.system.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class DocumentTreeBatchReq {

    @Size(max = 100, message = "节点一次最多加载100个")
    private List<@Valid Item> items;

    @Data
    public static class Item {

        @NotBlank(message = "节点key不能为空")
        @Size(max = 128, message = "节点key不能超过128个字符")
        private String key;

        @Size(max = 32, message = "范围不能超过32个字符")
        private String scope;

        @Size(max = 64, message = "父级ID不能超过64个字符")
        private String parentId;

        @Size(max = 16, message = "共享目标类型不能超过16个字符")
        private String shareTargetType;

        @Size(max = 64, message = "共享目标ID不能超过64个字符")
        private String shareTargetId;
    }
}
