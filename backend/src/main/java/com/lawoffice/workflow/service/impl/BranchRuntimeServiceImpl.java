package com.lawoffice.workflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.util.EntityFillUtils;
import com.lawoffice.workflow.entity.BranchRecord;
import com.lawoffice.workflow.mapper.BranchRecordMapper;
import com.lawoffice.workflow.req.BranchRecordReq;
import com.lawoffice.workflow.service.IBranchRuntimeService;
import com.lawoffice.workflow.vo.BranchRecordVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BranchRuntimeServiceImpl implements IBranchRuntimeService {

    private final BranchRecordMapper branchRecordMapper;

    public BranchRuntimeServiceImpl(BranchRecordMapper branchRecordMapper) {
        this.branchRecordMapper = branchRecordMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<BranchRecordVO> recordMatch(BranchRecordReq req, RequestContext context) {
        try {
            String tenantId = RuntimeSupport.requireTenantId(context);
            validateRecordReq(req);

            BranchRecord record = BeanUtil.toBean(req, BranchRecord.class);
            record.setTenantId(tenantId);
            record.setMatchedTime(LocalDateTime.now());
            EntityFillUtils.fillAuditFields(record, context, true);
            branchRecordMapper.insert(record);
            return BaseResult.success(BeanUtil.toBean(record, BranchRecordVO.class));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("记录条件分支命中失败: " + e.getMessage());
        }
    }

    @Override
    public BaseResult<List<BranchRecordVO>> listByInstance(String processInstanceId, RequestContext context) {
        try {
            String tenantId = RuntimeSupport.requireTenantId(context);
            if (!StringUtils.hasText(processInstanceId)) {
                throw new IllegalArgumentException("流程实例ID不能为空");
            }
            List<BranchRecord> records = branchRecordMapper.selectList(new QueryWrapper<BranchRecord>()
                    .eq("tenant_id", tenantId)
                    .eq("process_instance_id", processInstanceId)
                    .eq("delete_flag", 0)
                    .orderByAsc("matched_time"));
            return BaseResult.success(BeanUtil.copyToList(records, BranchRecordVO.class));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("查询条件分支记录失败: " + e.getMessage());
        }
    }

    private void validateRecordReq(BranchRecordReq req) {
        if (req == null) {
            throw new IllegalArgumentException("分支命中请求不能为空");
        }
        if (!StringUtils.hasText(req.getProcessInstanceId())) {
            throw new IllegalArgumentException("流程实例ID不能为空");
        }
        if (!StringUtils.hasText(req.getProcessModelId())) {
            throw new IllegalArgumentException("流程模型ID不能为空");
        }
        if (!StringUtils.hasText(req.getSourceNodeId())) {
            throw new IllegalArgumentException("来源节点ID不能为空");
        }
        if (!StringUtils.hasText(req.getBranchId())) {
            throw new IllegalArgumentException("分支ID不能为空");
        }
        if (!StringUtils.hasText(req.getTargetNodeId())) {
            throw new IllegalArgumentException("目标节点ID不能为空");
        }
    }
}
