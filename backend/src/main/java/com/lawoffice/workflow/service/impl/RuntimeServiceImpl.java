package com.lawoffice.workflow.service.impl;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.workflow.req.AvailableProcessPageReq;
import com.lawoffice.workflow.req.StartProcessReq;
import com.lawoffice.workflow.req.StartedInstancePageReq;
import com.lawoffice.workflow.req.TaskActionReq;
import com.lawoffice.workflow.req.TaskPageReq;
import com.lawoffice.workflow.service.IRuntimeQueryService;
import com.lawoffice.workflow.service.IRuntimeService;
import com.lawoffice.workflow.service.ITaskActionService;
import com.lawoffice.workflow.vo.AvailableProcessVO;
import com.lawoffice.workflow.vo.InstanceDetailVO;
import com.lawoffice.workflow.vo.OperationRecordVO;
import com.lawoffice.workflow.vo.RuntimeTaskVO;
import com.lawoffice.workflow.vo.StartFormVO;
import com.lawoffice.workflow.vo.StartProcessVO;
import com.lawoffice.workflow.vo.StartedInstanceVO;
import com.lawoffice.workflow.vo.TaskActionVO;
import com.lawoffice.workflow.vo.TaskFormVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RuntimeServiceImpl implements IRuntimeService {

    private final IRuntimeQueryService runtimeQueryService;
    private final ITaskActionService taskActionService;
    private final WorkflowRuntimeSupport runtimeSupport;

    public RuntimeServiceImpl(IRuntimeQueryService runtimeQueryService,
            ITaskActionService taskActionService,
            WorkflowRuntimeSupport runtimeSupport) {
        this.runtimeQueryService = runtimeQueryService;
        this.taskActionService = taskActionService;
        this.runtimeSupport = runtimeSupport;
    }

    @Override
    public BaseResult<PageVO<AvailableProcessVO>> pageAvailableProcesses(AvailableProcessPageReq req, RequestContext context) {
        return runtimeQueryService.pageAvailableProcesses(req, context);
    }

    @Override
    public BaseResult<StartFormVO> getStartForm(String processModelId, RequestContext context) {
        return runtimeQueryService.getStartForm(processModelId, context);
    }

    @Override
    public BaseResult<StartProcessVO> start(StartProcessReq req, RequestContext context) {
        return runtimeSupport.start(req, context);
    }

    @Override
    public BaseResult<StartProcessVO> saveStartDraft(StartProcessReq req, RequestContext context) {
        return runtimeSupport.saveStartDraft(req, context);
    }

    @Override
    public BaseResult<TaskActionVO> submitStartDraft(String taskId, TaskActionReq req, RequestContext context) {
        return taskActionService.submitStartDraft(taskId, req, context);
    }

    @Override
    public BaseResult<TaskActionVO> saveStartDraftTask(String taskId, TaskActionReq req, RequestContext context) {
        return taskActionService.saveStartDraftTask(taskId, req, context);
    }

    @Override
    public BaseResult<PageVO<StartedInstanceVO>> pageStartedInstances(StartedInstancePageReq req, RequestContext context) {
        return runtimeQueryService.pageStartedInstances(req, context);
    }

    @Override
    public BaseResult<PageVO<RuntimeTaskVO>> pageTodo(TaskPageReq req, RequestContext context) {
        return runtimeQueryService.pageTodo(req, context);
    }

    @Override
    public BaseResult<PageVO<RuntimeTaskVO>> pageDone(TaskPageReq req, RequestContext context) {
        return runtimeQueryService.pageDone(req, context);
    }

    @Override
    public BaseResult<TaskFormVO> getTaskForm(String taskId, RequestContext context) {
        return runtimeQueryService.getTaskForm(taskId, context);
    }

    @Override
    public BaseResult<TaskActionVO> approve(String taskId, TaskActionReq req, RequestContext context) {
        return taskActionService.approve(taskId, req, context);
    }

    @Override
    public BaseResult<TaskActionVO> reject(String taskId, TaskActionReq req, RequestContext context) {
        return taskActionService.reject(taskId, req, context);
    }

    @Override
    public BaseResult<TaskActionVO> transfer(String taskId, TaskActionReq req, RequestContext context) {
        return taskActionService.transfer(taskId, req, context);
    }

    @Override
    public BaseResult<TaskActionVO> returnTask(String taskId, TaskActionReq req, RequestContext context) {
        return taskActionService.returnTask(taskId, req, context);
    }

    @Override
    public BaseResult<TaskActionVO> addSign(String taskId, TaskActionReq req, RequestContext context) {
        return taskActionService.addSign(taskId, req, context);
    }

    @Override
    public BaseResult<InstanceDetailVO> getInstanceDetail(String id, RequestContext context) {
        return runtimeQueryService.getInstanceDetail(id, context);
    }

    @Override
    public BaseResult<List<OperationRecordVO>> listInstanceRecords(String id, RequestContext context) {
        return runtimeQueryService.listInstanceRecords(id, context);
    }
}
