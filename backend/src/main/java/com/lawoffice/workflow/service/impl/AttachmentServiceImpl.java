package com.lawoffice.workflow.service.impl;

import com.lawoffice.framework.service.impl.BaseServiceImpl;
import com.lawoffice.workflow.entity.Attachment;
import com.lawoffice.workflow.mapper.AttachmentMapper;
import com.lawoffice.workflow.service.IAttachmentService;
import com.lawoffice.workflow.vo.AttachmentVO;
import org.springframework.stereotype.Service;

@Service
public class AttachmentServiceImpl extends BaseServiceImpl<AttachmentMapper, Attachment, AttachmentVO>
        implements IAttachmentService {
}
