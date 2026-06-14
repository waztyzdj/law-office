package com.lawoffice.workflow.service.impl;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.workflow.req.TaskActionReq;
import com.lawoffice.workflow.service.ITaskActionService;
import com.lawoffice.workflow.vo.TaskActionVO;
import org.springframework.stereotype.Service;

@Service
public class TaskActionServiceImpl implements ITaskActionService {

    private final WorkflowRuntimeSupport runtimeSupport;

    public TaskActionServiceImpl(WorkflowRuntimeSupport runtimeSupport) {
        this.runtimeSupport = runtimeSupport;
    }

    @Override
    public BaseResult<TaskActionVO> submitStartDraft(String taskId, TaskActionReq req, RequestContext context) {
        return runtimeSupport.submitStartDraft(taskId, req, context);
    }

    @Override
    public BaseResult<TaskActionVO> saveStartDraftTask(String taskId, TaskActionReq req, RequestContext context) {
        return runtimeSupport.saveStartDraftTask(taskId, req, context);
    }

    @Override
    public BaseResult<TaskActionVO> approve(String taskId, TaskActionReq req, RequestContext context) {
        return runtimeSupport.approve(taskId, req, context);
    }

    @Override
    public BaseResult<TaskActionVO> reject(String taskId, TaskActionReq req, RequestContext context) {
        return runtimeSupport.reject(taskId, req, context);
    }

    @Override
    public BaseResult<TaskActionVO> transfer(String taskId, TaskActionReq req, RequestContext context) {
        return runtimeSupport.transfer(taskId, req, context);
    }

    @Override
    public BaseResult<TaskActionVO> returnTask(String taskId, TaskActionReq req, RequestContext context) {
        return runtimeSupport.returnTask(taskId, req, context);
    }

    @Override
    public BaseResult<TaskActionVO> addSign(String taskId, TaskActionReq req, RequestContext context) {
        return runtimeSupport.addSign(taskId, req, context);
    }
}
