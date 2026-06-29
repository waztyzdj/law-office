package com.lawoffice.workflow.service.impl;

import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.workflow.entity.AdminOperationRecord;
import com.lawoffice.workflow.mapper.AdminOperationRecordMapper;
import com.lawoffice.workflow.service.IAdminOperationRecordService;
import com.lawoffice.workflow.vo.AdminOperationRecordVO;
import org.springframework.stereotype.Service;

@Service
public class AdminOperationRecordServiceImpl
        extends BaseServiceImpl<AdminOperationRecordMapper, AdminOperationRecord, AdminOperationRecordVO>
        implements IAdminOperationRecordService {
}
