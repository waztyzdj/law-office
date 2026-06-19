package com.lawoffice.workflow.service.impl;

import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.workflow.entity.CcRecord;
import com.lawoffice.workflow.mapper.CcRecordMapper;
import com.lawoffice.workflow.service.ICcRecordService;
import com.lawoffice.workflow.vo.CcRecordVO;
import org.springframework.stereotype.Service;

@Service
public class CcRecordServiceImpl extends BaseServiceImpl<CcRecordMapper, CcRecord, CcRecordVO> implements ICcRecordService {
}
