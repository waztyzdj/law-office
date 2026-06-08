package com.lawoffice.workflow.service.impl;

import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.workflow.entity.OperationRecord;
import com.lawoffice.workflow.mapper.OperationRecordMapper;
import com.lawoffice.workflow.service.IOperationRecordService;
import com.lawoffice.workflow.vo.OperationRecordVO;
import org.springframework.stereotype.Service;

@Service
public class OperationRecordServiceImpl extends BaseServiceImpl<OperationRecordMapper, OperationRecord, OperationRecordVO> implements IOperationRecordService {
}
