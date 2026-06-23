package com.lawoffice.workflow.service;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.workflow.vo.ReminderRecordVO;

import java.util.List;

/**
 * 审批催办运行时服务。
 */
public interface IReminderRuntimeService {

    /**
     * 发起人按流程实例催办当前有效待办，仅生成提醒消息和催办记录，不改变流程状态或任务状态。
     *
     * @param processInstanceId 流程实例ID
     * @param remark 催办备注
     * @param context 请求上下文
     * @return 催办记录列表
     */
    BaseResult<List<ReminderRecordVO>> urge(String processInstanceId, String remark, RequestContext context);
}
