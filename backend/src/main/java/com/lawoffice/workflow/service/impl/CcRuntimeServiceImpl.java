package com.lawoffice.workflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.util.EntityFillUtils;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.CcRecord;
import com.lawoffice.workflow.mapper.CcRecordMapper;
import com.lawoffice.workflow.req.CcPageReq;
import com.lawoffice.workflow.service.ICcRuntimeService;
import com.lawoffice.workflow.vo.CcRecordVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CcRuntimeServiceImpl implements ICcRuntimeService {

    private final CcRecordMapper ccRecordMapper;

    public CcRuntimeServiceImpl(CcRecordMapper ccRecordMapper) {
        this.ccRecordMapper = ccRecordMapper;
    }

    @Override
    public BaseResult<PageVO<CcRecordVO>> pageMine(CcPageReq req, RequestContext context) {
        try {
            String tenantId = RuntimeSupport.requireTenantId(context);
            String userId = RuntimeSupport.requireUserId(context);
            CcPageReq query = req == null ? new CcPageReq() : req;

            QueryWrapper<CcRecord> wrapper = new QueryWrapper<>();
            wrapper.eq("tenant_id", tenantId)
                    .eq("receiver_user_id", userId)
                    .eq("delete_flag", 0);
            if (StringUtils.hasText(query.getProcessInstanceId())) {
                wrapper.eq("process_instance_id", query.getProcessInstanceId());
            }
            if (StringUtils.hasText(query.getStatus())) {
                wrapper.eq("status", query.getStatus());
            }
            wrapper.orderByDesc("create_time");

            Page<CcRecord> page = new Page<>(query.getPageNum(), query.getPageSize());
            Page<CcRecord> resultPage = ccRecordMapper.selectPage(page, wrapper);
            List<CcRecordVO> records = BeanUtil.copyToList(resultPage.getRecords(), CcRecordVO.class);
            return BaseResult.success(new PageVO<>(records, resultPage.getTotal(), resultPage.getCurrent(), resultPage.getSize()));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("查询我的抄送失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<CcRecordVO> markRead(String ccRecordId, RequestContext context) {
        try {
            String tenantId = RuntimeSupport.requireTenantId(context);
            String userId = RuntimeSupport.requireUserId(context);
            CcRecord record = requireMine(ccRecordId, tenantId, userId);
            if (WorkflowConstants.CcStatus.READ.equals(record.getStatus())) {
                return BaseResult.success(BeanUtil.toBean(record, CcRecordVO.class));
            }

            record.setStatus(WorkflowConstants.CcStatus.READ);
            record.setReadTime(LocalDateTime.now());
            EntityFillUtils.fillAuditFields(record, context, false);
            ccRecordMapper.updateById(record);
            return BaseResult.success(BeanUtil.toBean(record, CcRecordVO.class));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("标记抄送已读失败: " + e.getMessage());
        }
    }

    private CcRecord requireMine(String ccRecordId, String tenantId, String userId) {
        if (!StringUtils.hasText(ccRecordId)) {
            throw new IllegalArgumentException("抄送记录ID不能为空");
        }
        CcRecord record = ccRecordMapper.selectOne(new QueryWrapper<CcRecord>()
                .eq("id", ccRecordId)
                .eq("tenant_id", tenantId)
                .eq("receiver_user_id", userId)
                .eq("delete_flag", 0));
        if (record == null) {
            throw new IllegalArgumentException("抄送记录不存在或无权访问");
        }
        return record;
    }
}
