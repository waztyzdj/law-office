package com.lawoffice.workflow.vo;

import lombok.Data;

@Data
public class TaskActionPermissionVO {

    private Boolean allowApprove;

    private Boolean allowReject;

    private Boolean allowTransfer;

    private Boolean allowReturn;

    private Boolean allowAddSign;
}
