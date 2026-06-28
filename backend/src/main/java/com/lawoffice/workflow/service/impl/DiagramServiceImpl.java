package com.lawoffice.workflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.workflow.entity.BranchRecord;
import com.lawoffice.workflow.entity.OperationRecord;
import com.lawoffice.workflow.entity.ProcessInstance;
import com.lawoffice.workflow.entity.ProcessModel;
import com.lawoffice.workflow.mapper.BranchRecordMapper;
import com.lawoffice.workflow.mapper.OperationRecordMapper;
import com.lawoffice.workflow.mapper.ProcessInstanceMapper;
import com.lawoffice.workflow.mapper.ProcessModelMapper;
import com.lawoffice.workflow.service.IDiagramService;
import com.lawoffice.workflow.service.IRuntimeAccessService;
import com.lawoffice.workflow.vo.BranchRecordVO;
import com.lawoffice.workflow.vo.InstanceDiagramVO;
import com.lawoffice.workflow.vo.OperationRecordVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class DiagramServiceImpl implements IDiagramService {

    private final ProcessInstanceMapper processInstanceMapper;
    private final ProcessModelMapper processModelMapper;
    private final BranchRecordMapper branchRecordMapper;
    private final OperationRecordMapper operationRecordMapper;
    private final IRuntimeAccessService runtimeAccessService;

    public DiagramServiceImpl(ProcessInstanceMapper processInstanceMapper,
            ProcessModelMapper processModelMapper,
            BranchRecordMapper branchRecordMapper,
            OperationRecordMapper operationRecordMapper,
            IRuntimeAccessService runtimeAccessService) {
        this.processInstanceMapper = processInstanceMapper;
        this.processModelMapper = processModelMapper;
        this.branchRecordMapper = branchRecordMapper;
        this.operationRecordMapper = operationRecordMapper;
        this.runtimeAccessService = runtimeAccessService;
    }

    @Override
    public BaseResult<InstanceDiagramVO> getInstanceDiagram(String processInstanceId, RequestContext context) {
        try {
            String tenantId = RuntimeSupport.requireTenantId(context);
            if (!StringUtils.hasText(processInstanceId)) {
                throw new IllegalArgumentException("流程实例ID不能为空");
            }
            ProcessInstance instance = processInstanceMapper.selectOne(new QueryWrapper<ProcessInstance>()
                    .eq("id", processInstanceId)
                    .eq("tenant_id", tenantId)
                    .eq("delete_flag", 0));
            if (instance == null) {
                throw new IllegalArgumentException("流程实例不存在");
            }
            runtimeAccessService.ensureInstanceAccess(instance, context);
            ProcessModel model = processModelMapper.selectOne(new QueryWrapper<ProcessModel>()
                    .eq("id", instance.getProcessModelId())
                    .eq("tenant_id", tenantId)
                    .eq("delete_flag", 0));
            if (model == null) {
                throw new IllegalArgumentException("流程模型不存在");
            }

            InstanceDiagramVO diagram = new InstanceDiagramVO();
            diagram.setProcessInstanceId(instance.getId());
            diagram.setProcessModelId(model.getId());
            diagram.setBpmnXml(model.getBpmnXml());
            diagram.setBranchRecords(listBranchRecords(tenantId, instance.getId()));
            diagram.setOperationRecords(listOperationRecords(tenantId, instance.getId()));
            return BaseResult.success(diagram);
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("查询流程图数据失败: " + e.getMessage());
        }
    }

    private List<BranchRecordVO> listBranchRecords(String tenantId, String processInstanceId) {
        List<BranchRecord> records = branchRecordMapper.selectList(new QueryWrapper<BranchRecord>()
                .eq("tenant_id", tenantId)
                .eq("process_instance_id", processInstanceId)
                .eq("delete_flag", 0)
                .orderByAsc("matched_time"));
        return BeanUtil.copyToList(records, BranchRecordVO.class);
    }

    private List<OperationRecordVO> listOperationRecords(String tenantId, String processInstanceId) {
        List<OperationRecord> records = operationRecordMapper.selectList(new QueryWrapper<OperationRecord>()
                .eq("tenant_id", tenantId)
                .eq("process_instance_id", processInstanceId)
                .eq("delete_flag", 0)
                .orderByAsc("operate_time"));
        return BeanUtil.copyToList(records, OperationRecordVO.class);
    }
}
