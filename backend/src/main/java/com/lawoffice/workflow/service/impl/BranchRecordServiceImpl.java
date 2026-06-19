package com.lawoffice.workflow.service.impl;

import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.workflow.entity.BranchRecord;
import com.lawoffice.workflow.mapper.BranchRecordMapper;
import com.lawoffice.workflow.service.IBranchRecordService;
import com.lawoffice.workflow.vo.BranchRecordVO;
import org.springframework.stereotype.Service;

@Service
public class BranchRecordServiceImpl extends BaseServiceImpl<BranchRecordMapper, BranchRecord, BranchRecordVO> implements IBranchRecordService {
}
