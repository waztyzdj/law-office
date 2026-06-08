package com.lawoffice.workflow.req;

import lombok.Data;

@Data
public class StartProcessReq {

    private String processModelId;

    private String instanceTitle;

    private String businessKey;

    private String formDataJson;
}
