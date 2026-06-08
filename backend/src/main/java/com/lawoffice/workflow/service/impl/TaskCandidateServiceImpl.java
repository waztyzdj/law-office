package com.lawoffice.workflow.service.impl;

import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.workflow.entity.TaskCandidate;
import com.lawoffice.workflow.mapper.TaskCandidateMapper;
import com.lawoffice.workflow.service.ITaskCandidateService;
import com.lawoffice.workflow.vo.TaskCandidateVO;
import org.springframework.stereotype.Service;

@Service
public class TaskCandidateServiceImpl extends BaseServiceImpl<TaskCandidateMapper, TaskCandidate, TaskCandidateVO> implements ITaskCandidateService {
}
