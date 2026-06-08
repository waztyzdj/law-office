package com.lawoffice.workflow.service.impl;

import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.mapper.ProcessInstanceMapper;
import com.lawoffice.workflow.service.IProcessInstanceService;
import com.lawoffice.workflow.vo.ProcessInstanceVO;
import org.springframework.stereotype.Service;

@Service
public class ProcessInstanceServiceImpl extends BaseServiceImpl<ProcessInstanceMapper, ProcessInstance, ProcessInstanceVO> implements IProcessInstanceService {
}
