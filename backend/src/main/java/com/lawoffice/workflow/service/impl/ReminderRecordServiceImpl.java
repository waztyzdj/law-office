package com.lawoffice.workflow.service.impl;

import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.workflow.entity.ReminderRecord;
import com.lawoffice.workflow.mapper.ReminderRecordMapper;
import com.lawoffice.workflow.service.IReminderRecordService;
import com.lawoffice.workflow.vo.ReminderRecordVO;
import org.springframework.stereotype.Service;

@Service
public class ReminderRecordServiceImpl extends BaseServiceImpl<ReminderRecordMapper, ReminderRecord, ReminderRecordVO> implements IReminderRecordService {
}
