package com.lawoffice.workflow.service;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.workflow.vo.ReminderRecordVO;

/**
 * 审批催办运行时服务。
 */
public interface IReminderRuntimeService {

    /**
     * 对当前待办任务发送人工催办，并记录防重复审计信息。
     *
     * @param taskId 任务扩展ID
     * @param remark 催办备注
     * @param context 请求上下文
     * @return 催办记录
     */
    BaseResult<ReminderRecordVO> urgeTask(String taskId, String remark, RequestContext context);
}
