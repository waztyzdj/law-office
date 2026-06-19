package com.lawoffice.workflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.util.EntityFillUtils;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.ProcessModel;
import com.lawoffice.workflow.mapper.ProcessModelMapper;
import com.lawoffice.workflow.service.IBpmnSecurityService;
import com.lawoffice.workflow.vo.ProcessModelVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class BpmnSecurityServiceImpl implements IBpmnSecurityService {

    private final ProcessModelMapper processModelMapper;

    public BpmnSecurityServiceImpl(ProcessModelMapper processModelMapper) {
        this.processModelMapper = processModelMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<ProcessModelVO> validateModel(String processModelId, RequestContext context) {
        try {
            String tenantId = RuntimeSupport.requireTenantId(context);
            if (!StringUtils.hasText(processModelId)) {
                throw new IllegalArgumentException("流程模型ID不能为空");
            }
            ProcessModel model = processModelMapper.selectOne(new QueryWrapper<ProcessModel>()
                    .eq("id", processModelId)
                    .eq("tenant_id", tenantId)
                    .eq("delete_flag", 0));
            if (model == null) {
                throw new IllegalArgumentException("流程模型不存在");
            }
            String message = validateBpmnXml(model.getBpmnXml());
            model.setBpmnSecurityStatus(WorkflowConstants.BpmnSecurityStatus.PASSED);
            model.setBpmnSecurityMessage(message);
            EntityFillUtils.fillAuditFields(model, context, false);
            processModelMapper.updateById(model);
            return BaseResult.success(BeanUtil.toBean(model, ProcessModelVO.class));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("BPMN安全校验失败: " + e.getMessage());
        }
    }

    private String validateBpmnXml(String bpmnXml) {
        if (!StringUtils.hasText(bpmnXml)) {
            throw new IllegalArgumentException("BPMN XML不能为空");
        }
        if (bpmnXml.contains("<scriptTask") || bpmnXml.contains(":scriptTask")) {
            throw new IllegalArgumentException("BPMN暂不允许使用脚本任务");
        }
        if (bpmnXml.contains("<serviceTask") || bpmnXml.contains(":serviceTask")) {
            throw new IllegalArgumentException("BPMN暂不允许使用服务任务");
        }
        return "BPMN安全校验通过";
    }
}
