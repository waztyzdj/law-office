package com.lawoffice.workflow.service.impl;

import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.workflow.req.AvailableProcessPageReq;
import com.lawoffice.workflow.req.StartedInstancePageReq;
import com.lawoffice.workflow.req.TaskPageReq;
import com.lawoffice.workflow.service.IRuntimeQueryService;
import com.lawoffice.workflow.vo.AvailableProcessVO;
import com.lawoffice.workflow.vo.InstanceDetailVO;
import com.lawoffice.workflow.vo.OperationRecordVO;
import com.lawoffice.workflow.vo.RuntimeTaskVO;
import com.lawoffice.workflow.vo.StartFormVO;
import com.lawoffice.workflow.vo.StartedInstanceVO;
import com.lawoffice.workflow.vo.TaskFormVO;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RuntimeQueryServiceImpl implements IRuntimeQueryService {

    private final WorkflowRuntimeSupport runtimeSupport;

    public RuntimeQueryServiceImpl(WorkflowRuntimeSupport runtimeSupport) {
        this.runtimeSupport = runtimeSupport;
    }

    @Override
    public BaseResult<PageVO<AvailableProcessVO>> pageAvailableProcesses(AvailableProcessPageReq req, RequestContext context) {
        return runtimeSupport.pageAvailableProcesses(req, context);
    }

    @Override
    public BaseResult<StartFormVO> getStartForm(String processModelId, RequestContext context) {
        return runtimeSupport.getStartForm(processModelId, context);
    }

    @Override
    public BaseResult<PageVO<StartedInstanceVO>> pageStartedInstances(StartedInstancePageReq req, RequestContext context) {
        return runtimeSupport.pageStartedInstances(req, context);
    }

    @Override
    public BaseResult<PageVO<RuntimeTaskVO>> pageTodo(TaskPageReq req, RequestContext context) {
        return runtimeSupport.pageTodo(req, context);
    }

    @Override
    public BaseResult<PageVO<RuntimeTaskVO>> pageDone(TaskPageReq req, RequestContext context) {
        return runtimeSupport.pageDone(req, context);
    }

    @Override
    public BaseResult<TaskFormVO> getTaskForm(String taskId, RequestContext context) {
        return runtimeSupport.getTaskForm(taskId, context);
    }

    @Override
    public BaseResult<InstanceDetailVO> getInstanceDetail(String id, RequestContext context) {
        return runtimeSupport.getInstanceDetail(id, context);
    }

    @Override
    public BaseResult<List<OperationRecordVO>> listInstanceRecords(String id, RequestContext context) {
        return runtimeSupport.listInstanceRecords(id, context);
    }
}
