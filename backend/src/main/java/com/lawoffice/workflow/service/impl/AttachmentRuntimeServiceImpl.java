package com.lawoffice.workflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.util.EntityFillUtils;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.Attachment;
import com.lawoffice.workflow.mapper.AttachmentMapper;
import com.lawoffice.workflow.req.AttachmentBindReq;
import com.lawoffice.workflow.service.IAttachmentRuntimeService;
import com.lawoffice.workflow.vo.AttachmentVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class AttachmentRuntimeServiceImpl implements IAttachmentRuntimeService {

    private final AttachmentMapper attachmentMapper;

    public AttachmentRuntimeServiceImpl(AttachmentMapper attachmentMapper) {
        this.attachmentMapper = attachmentMapper;
    }

    @Override
    public BaseResult<List<AttachmentVO>> listByInstance(String processInstanceId, RequestContext context) {
        try {
            String tenantId = RuntimeSupport.requireTenantId(context);
            if (!StringUtils.hasText(processInstanceId)) {
                throw new IllegalArgumentException("流程实例ID不能为空");
            }
            List<Attachment> attachments = attachmentMapper.selectList(new QueryWrapper<Attachment>()
                    .eq("tenant_id", tenantId)
                    .eq("process_instance_id", processInstanceId)
                    .eq("status", WorkflowConstants.AttachmentStatus.ACTIVE)
                    .eq("delete_flag", 0)
                    .orderByAsc("sort_order")
                    .orderByAsc("create_time"));
            return BaseResult.success(BeanUtil.copyToList(attachments, AttachmentVO.class));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("查询审批附件失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<AttachmentVO> bind(AttachmentBindReq req, RequestContext context) {
        try {
            String tenantId = RuntimeSupport.requireTenantId(context);
            String userId = RuntimeSupport.requireUserId(context);
            validateBindReq(req);

            Attachment attachment = new Attachment();
            attachment.setTenantId(tenantId);
            attachment.setProcessInstanceId(req.getProcessInstanceId());
            attachment.setTaskId(req.getTaskId());
            attachment.setNodeId(req.getNodeId());
            attachment.setNodeName(req.getNodeName());
            attachment.setFileId(req.getFileId());
            attachment.setAttachmentSource(req.getAttachmentSource());
            attachment.setUploaderUserId(userId);
            attachment.setUploaderUsername(context.getUsername());
            attachment.setStatus(WorkflowConstants.AttachmentStatus.ACTIVE);
            attachment.setSortOrder(0);
            attachment.setRemark(req.getRemark());
            EntityFillUtils.fillAuditFields(attachment, context, true);
            attachmentMapper.insert(attachment);
            return BaseResult.success(BeanUtil.toBean(attachment, AttachmentVO.class));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("绑定审批附件失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<Void> delete(String attachmentId, RequestContext context) {
        try {
            String tenantId = RuntimeSupport.requireTenantId(context);
            RuntimeSupport.requireUserId(context);
            if (!StringUtils.hasText(attachmentId)) {
                throw new IllegalArgumentException("附件ID不能为空");
            }
            Attachment attachment = attachmentMapper.selectOne(new QueryWrapper<Attachment>()
                    .eq("id", attachmentId)
                    .eq("tenant_id", tenantId)
                    .eq("delete_flag", 0));
            if (attachment == null) {
                throw new IllegalArgumentException("审批附件不存在");
            }
            attachment.setStatus(WorkflowConstants.AttachmentStatus.DELETED);
            EntityFillUtils.fillDeleteFields(attachment, RuntimeSupport.username(context));
            attachmentMapper.updateById(attachment);
            return BaseResult.success();
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("删除审批附件失败: " + e.getMessage());
        }
    }

    private void validateBindReq(AttachmentBindReq req) {
        if (req == null) {
            throw new IllegalArgumentException("附件绑定请求不能为空");
        }
        if (!StringUtils.hasText(req.getProcessInstanceId())) {
            throw new IllegalArgumentException("流程实例ID不能为空");
        }
        if (!StringUtils.hasText(req.getFileId())) {
            throw new IllegalArgumentException("文件ID不能为空");
        }
        if (!StringUtils.hasText(req.getAttachmentSource())) {
            throw new IllegalArgumentException("附件来源不能为空");
        }
    }
}
