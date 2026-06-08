package com.lawoffice.workflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.util.EntityFillUtils;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.dto.FlowableDeploymentResult;
import com.lawoffice.workflow.entity.FieldPermission;
import com.lawoffice.workflow.entity.FormDefinition;
import com.lawoffice.workflow.entity.ProcessModel;
import com.lawoffice.workflow.entity.ProcessNodeConfig;
import com.lawoffice.workflow.entity.ProcessStartPermission;
import com.lawoffice.workflow.mapper.FieldPermissionMapper;
import com.lawoffice.workflow.mapper.FormDefinitionMapper;
import com.lawoffice.workflow.mapper.ProcessCategoryMapper;
import com.lawoffice.workflow.mapper.ProcessInstanceMapper;
import com.lawoffice.workflow.mapper.ProcessModelMapper;
import com.lawoffice.workflow.mapper.ProcessNodeConfigMapper;
import com.lawoffice.workflow.mapper.ProcessStartPermissionMapper;
import com.lawoffice.workflow.service.IFlowableService;
import com.lawoffice.workflow.service.IProcessModelService;
import com.lawoffice.workflow.vo.ProcessModelVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProcessModelServiceImpl extends AbstractWorkflowConfigServiceImpl<ProcessModelMapper, ProcessModel, ProcessModelVO> implements IProcessModelService {

    private final ProcessCategoryMapper processCategoryMapper;
    private final FormDefinitionMapper formDefinitionMapper;
    private final ProcessInstanceMapper processInstanceMapper;
    private final ProcessNodeConfigMapper processNodeConfigMapper;
    private final FieldPermissionMapper fieldPermissionMapper;
    private final ProcessStartPermissionMapper processStartPermissionMapper;
    private final IFlowableService flowableService;

    @Autowired
    public ProcessModelServiceImpl(ProcessCategoryMapper processCategoryMapper,
            FormDefinitionMapper formDefinitionMapper,
            ProcessInstanceMapper processInstanceMapper,
            ProcessNodeConfigMapper processNodeConfigMapper,
            FieldPermissionMapper fieldPermissionMapper,
            ProcessStartPermissionMapper processStartPermissionMapper,
            IFlowableService flowableService) {
        this.processCategoryMapper = processCategoryMapper;
        this.formDefinitionMapper = formDefinitionMapper;
        this.processInstanceMapper = processInstanceMapper;
        this.processNodeConfigMapper = processNodeConfigMapper;
        this.fieldPermissionMapper = fieldPermissionMapper;
        this.processStartPermissionMapper = processStartPermissionMapper;
        this.flowableService = flowableService;
    }

    @Override
    protected void doBeforeSave(BaseDTO<ProcessModel> saveDTO) {
        ProcessModel model = saveDTO == null ? null : saveDTO.getEntity();
        prepareTenant(model, saveDTO);
        normalize(model);

        if (StringUtils.hasText(model.getId())) {
            ProcessModel current = requireCurrent(model.getId(), model.getTenantId(), "流程模型不存在");
            if (WorkflowConstants.Status.PUBLISHED.equals(current.getStatus())) {
                throw new IllegalArgumentException("已发布流程版本不可直接修改，请复制为新版本草稿后调整");
            }
        }

        requireText(model.getProcessKey(), "流程标识不能为空");
        requireText(model.getProcessName(), "流程名称不能为空");
        requireActiveById(processCategoryMapper, model.getCategoryId(), model.getTenantId(), "流程分类不存在");
        requireActiveById(formDefinitionMapper, model.getFormDefinitionId(), model.getTenantId(), "表单定义不存在");
        validateJson(model.getNodeJson(), "简单设计器节点JSON", false);
        validateUnique(model, "同一租户下流程标识和版本不能重复",
                "process_key", model.getProcessKey(),
                "version", model.getVersion());
    }

    @Override
    protected void doBeforeDelete(BaseDTO<ProcessModel> deleteDTO) {
        for (String id : resolveDeleteIds(deleteDTO)) {
            String tenantId = resolveTenantId(null, deleteDTO.getContext());
            ProcessModel model = requireCurrent(id, tenantId, "流程模型不存在");
            if (WorkflowConstants.Status.PUBLISHED.equals(model.getStatus())) {
                throw new IllegalArgumentException("已发布流程版本不可删除");
            }
            if (countActive(processInstanceMapper, tenantId, "process_model_id", id) > 0) {
                throw new IllegalArgumentException("流程已有实例数据，不能删除");
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    protected void doAfterDelete(BaseDTO<ProcessModel> deleteDTO) {
        String tenantId = resolveTenantId(null, deleteDTO.getContext());
        for (String id : resolveDeleteIds(deleteDTO)) {
            logicDeleteChildren(processNodeConfigMapper, tenantId, id, deleteDTO.getContext());
            logicDeleteChildren(fieldPermissionMapper, tenantId, id, deleteDTO.getContext());
            logicDeleteChildren(processStartPermissionMapper, tenantId, id, deleteDTO.getContext());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<ProcessModelVO> publish(String id, RequestContext context) {
        try {
            String tenantId = resolveTenantId(null, context);
            ProcessModel model = requireCurrent(id, tenantId, "流程模型不存在");
            if (WorkflowConstants.Status.PUBLISHED.equals(model.getStatus())) {
                return BaseResult.success(BeanUtil.toBean(model, ProcessModelVO.class));
            }
            if (!WorkflowConstants.Status.DRAFT.equals(model.getStatus())) {
                throw new IllegalArgumentException("只有草稿流程可以发布");
            }
            validateBeforePublish(model);
            FlowableDeploymentResult deploymentResult = flowableService.deployProcessModel(model);
            model.setFlowableDeploymentId(deploymentResult.getDeploymentId());
            model.setFlowableProcessDefinitionId(deploymentResult.getProcessDefinitionId());
            model.setStatus(WorkflowConstants.Status.PUBLISHED);
            model.setPublishedTime(LocalDateTime.now());
            fillUpdate(model, context);
            updateById(model);
            return BaseResult.success(BeanUtil.toBean(model, ProcessModelVO.class));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("发布流程失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<ProcessModelVO> copyAsDraft(String id, RequestContext context) {
        try {
            String tenantId = resolveTenantId(null, context);
            ProcessModel source = requireCurrent(id, tenantId, "流程模型不存在");
            ProcessModel draft = BeanUtil.copyProperties(source, ProcessModel.class);
            draft.setId(null);
            draft.setVersion(resolveNextVersion(source.getTenantId(), source.getProcessKey()));
            draft.setStatus(WorkflowConstants.Status.DRAFT);
            draft.setPublishedTime(null);
            draft.setFlowableDeploymentId(null);
            draft.setFlowableProcessDefinitionId(null);
            EntityFillUtils.fillAuditFields(draft, context, true);
            save(draft);
            copyChildren(source.getId(), draft.getId(), tenantId, context);
            return BaseResult.success(BeanUtil.toBean(draft, ProcessModelVO.class));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("复制流程版本失败: " + e.getMessage());
        }
    }

    private void normalize(ProcessModel model) {
        model.setProcessKey(trimToNull(model.getProcessKey()));
        model.setProcessName(trimToNull(model.getProcessName()));
        if (!StringUtils.hasText(model.getStatus())) {
            model.setStatus(WorkflowConstants.Status.DRAFT);
        }
        validateIn(model.getStatus(), "流程状态不合法",
                WorkflowConstants.Status.DRAFT,
                WorkflowConstants.Status.PUBLISHED,
                WorkflowConstants.Status.DISABLED);
        if (!StringUtils.hasText(model.getDesignerType())) {
            model.setDesignerType(WorkflowConstants.DesignerType.SIMPLE);
        }
        validateIn(model.getDesignerType(), "设计器类型不合法",
                WorkflowConstants.DesignerType.SIMPLE,
                WorkflowConstants.DesignerType.BPMN);
        if (!StringUtils.hasText(model.getStartScopeType())) {
            model.setStartScopeType(WorkflowConstants.StartScopeType.ALL);
        }
        validateIn(model.getStartScopeType(), "发起范围不合法",
                WorkflowConstants.StartScopeType.ALL,
                WorkflowConstants.StartScopeType.SPECIFIED);
        if (model.getVersion() == null) {
            model.setVersion(resolveNextVersion(model.getTenantId(), model.getProcessKey()));
        }
    }

    private void validateBeforePublish(ProcessModel model) {
        validateJson(model.getNodeJson(), "简单设计器节点JSON", false);
        requireText(model.getBpmnXml(), "BPMN XML不能为空");
        FormDefinition form = requireActiveById(formDefinitionMapper, model.getFormDefinitionId(), model.getTenantId(), "表单定义不存在");
        if (!WorkflowConstants.Status.PUBLISHED.equals(form.getStatus())) {
            throw new IllegalArgumentException("流程发布必须绑定已发布表单版本");
        }
        if (countActive(processNodeConfigMapper, model.getTenantId(),
                "process_model_id", model.getId(),
                "node_type", WorkflowConstants.NodeType.APPROVER) == 0) {
            throw new IllegalArgumentException("流程至少需要配置一个审批节点");
        }
        if (WorkflowConstants.StartScopeType.SPECIFIED.equals(model.getStartScopeType())
                && countActive(processStartPermissionMapper, model.getTenantId(), "process_model_id", model.getId()) == 0) {
            throw new IllegalArgumentException("指定发起范围时必须配置发起权限");
        }
    }

    private Integer resolveNextVersion(String tenantId, String processKey) {
        if (!StringUtils.hasText(processKey)) {
            return 1;
        }
        QueryWrapper<ProcessModel> wrapper = new QueryWrapper<>();
        wrapper.select("max(version) as version")
                .eq("tenant_id", tenantId)
                .eq("process_key", processKey)
                .eq("delete_flag", 0);
        ProcessModel latest = baseMapper.selectOne(wrapper);
        return latest == null || latest.getVersion() == null ? 1 : latest.getVersion() + 1;
    }

    private void copyChildren(String sourceModelId, String targetModelId, String tenantId, RequestContext context) {
        copyNodeConfigs(sourceModelId, targetModelId, tenantId, context);
        copyFieldPermissions(sourceModelId, targetModelId, tenantId, context);
        copyStartPermissions(sourceModelId, targetModelId, tenantId, context);
    }

    private void copyNodeConfigs(String sourceModelId, String targetModelId, String tenantId, RequestContext context) {
        QueryWrapper<ProcessNodeConfig> wrapper = activeChildrenWrapper(sourceModelId, tenantId);
        List<ProcessNodeConfig> configs = processNodeConfigMapper.selectList(wrapper);
        for (ProcessNodeConfig source : configs) {
            ProcessNodeConfig target = BeanUtil.copyProperties(source, ProcessNodeConfig.class);
            target.setId(null);
            target.setProcessModelId(targetModelId);
            EntityFillUtils.fillAuditFields(target, context, true);
            processNodeConfigMapper.insert(target);
        }
    }

    private void copyFieldPermissions(String sourceModelId, String targetModelId, String tenantId, RequestContext context) {
        QueryWrapper<FieldPermission> wrapper = activeChildrenWrapper(sourceModelId, tenantId);
        List<FieldPermission> permissions = fieldPermissionMapper.selectList(wrapper);
        for (FieldPermission source : permissions) {
            FieldPermission target = BeanUtil.copyProperties(source, FieldPermission.class);
            target.setId(null);
            target.setProcessModelId(targetModelId);
            EntityFillUtils.fillAuditFields(target, context, true);
            fieldPermissionMapper.insert(target);
        }
    }

    private void copyStartPermissions(String sourceModelId, String targetModelId, String tenantId, RequestContext context) {
        QueryWrapper<ProcessStartPermission> wrapper = activeChildrenWrapper(sourceModelId, tenantId);
        List<ProcessStartPermission> permissions = processStartPermissionMapper.selectList(wrapper);
        for (ProcessStartPermission source : permissions) {
            ProcessStartPermission target = BeanUtil.copyProperties(source, ProcessStartPermission.class);
            target.setId(null);
            target.setProcessModelId(targetModelId);
            EntityFillUtils.fillAuditFields(target, context, true);
            processStartPermissionMapper.insert(target);
        }
    }

    private <T> QueryWrapper<T> activeChildrenWrapper(String processModelId, String tenantId) {
        QueryWrapper<T> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId)
                .eq("process_model_id", processModelId)
                .eq("delete_flag", 0);
        return wrapper;
    }

    private <T extends com.lawoffice.framework.entity.BaseEntity> void logicDeleteChildren(
            com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper,
            String tenantId,
            String processModelId,
            RequestContext context) {
        QueryWrapper<T> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId)
                .eq("process_model_id", processModelId)
                .eq("delete_flag", 0);
        List<T> children = mapper.selectList(wrapper);
        for (T child : children) {
            EntityFillUtils.fillDeleteFields(child, context == null ? "system" : context.getUsername());
            mapper.updateById(child);
        }
    }
}
