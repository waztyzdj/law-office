package com.lawoffice.workflow.service.impl;

import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.workflow.entity.Task;
import com.lawoffice.workflow.mapper.TaskMapper;
import com.lawoffice.workflow.service.ITaskService;
import com.lawoffice.workflow.vo.TaskVO;
import org.springframework.stereotype.Service;

@Service
public class TaskServiceImpl extends BaseServiceImpl<TaskMapper, Task, TaskVO> implements ITaskService {
}
