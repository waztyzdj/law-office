package com.lawoffice.workflow.service.impl;

import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.workflow.entity.FormInstance;
import com.lawoffice.workflow.mapper.FormInstanceMapper;
import com.lawoffice.workflow.service.IFormInstanceService;
import com.lawoffice.workflow.vo.FormInstanceVO;
import org.springframework.stereotype.Service;

@Service
public class FormInstanceServiceImpl extends BaseServiceImpl<FormInstanceMapper, FormInstance, FormInstanceVO> implements IFormInstanceService {
}
