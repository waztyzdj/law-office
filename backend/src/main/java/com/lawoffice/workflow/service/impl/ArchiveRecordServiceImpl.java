package com.lawoffice.workflow.service.impl;

import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.workflow.entity.ArchiveRecord;
import com.lawoffice.workflow.mapper.ArchiveRecordMapper;
import com.lawoffice.workflow.service.IArchiveRecordService;
import com.lawoffice.workflow.vo.ArchiveRecordVO;
import org.springframework.stereotype.Service;

@Service
public class ArchiveRecordServiceImpl
        extends BaseServiceImpl<ArchiveRecordMapper, ArchiveRecord, ArchiveRecordVO>
        implements IArchiveRecordService {
}
