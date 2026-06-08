package com.lawoffice.workflow.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.util.EntityFillUtils;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.FormDefinition;
import com.lawoffice.workflow.mapper.FormDefinitionMapper;
import com.lawoffice.workflow.mapper.FormInstanceMapper;
import com.lawoffice.workflow.mapper.ProcessCategoryMapper;
import com.lawoffice.workflow.mapper.ProcessModelMapper;
import com.lawoffice.workflow.service.IFormDefinitionService;
import com.lawoffice.workflow.vo.FormDefinitionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class FormDefinitionServiceImpl extends AbstractWorkflowConfigServiceImpl<FormDefinitionMapper, FormDefinition, FormDefinitionVO> implements IFormDefinitionService {

    private final ProcessCategoryMapper processCategoryMapper;
    private final ProcessModelMapper processModelMapper;
    private final FormInstanceMapper formInstanceMapper;

    @Autowired
    public FormDefinitionServiceImpl(ProcessCategoryMapper processCategoryMapper,
            ProcessModelMapper processModelMapper,
            FormInstanceMapper formInstanceMapper) {
        this.processCategoryMapper = processCategoryMapper;
        this.processModelMapper = processModelMapper;
        this.formInstanceMapper = formInstanceMapper;
    }

    @Override
    protected void doBeforeSave(BaseDTO<FormDefinition> saveDTO) {
        FormDefinition form = saveDTO == null ? null : saveDTO.getEntity();
        prepareTenant(form, saveDTO);
        normalize(form);

        if (StringUtils.hasText(form.getId())) {
            FormDefinition current = requireCurrent(form.getId(), form.getTenantId(), "表单定义不存在");
            if (WorkflowConstants.Status.PUBLISHED.equals(current.getStatus())) {
                throw new IllegalArgumentException("已发布表单版本不可直接修改，请复制为新版本草稿后调整");
            }
        }

        requireText(form.getFormKey(), "表单标识不能为空");
        requireText(form.getFormName(), "表单名称不能为空");
        requireActiveById(processCategoryMapper, form.getCategoryId(), form.getTenantId(), "流程分类不存在");
        validateJson(form.getSchemaJson(), "表单规则JSON", false);
        validateJson(form.getOptionJson(), "表单选项JSON", false);
        validateUnique(form, "同一租户下表单标识和版本不能重复",
                "form_key", form.getFormKey(),
                "version", form.getVersion());
    }

    @Override
    protected void doBeforeDelete(BaseDTO<FormDefinition> deleteDTO) {
        for (String id : resolveDeleteIds(deleteDTO)) {
            String tenantId = resolveTenantId(null, deleteDTO.getContext());
            FormDefinition form = requireCurrent(id, tenantId, "表单定义不存在");
            if (WorkflowConstants.Status.PUBLISHED.equals(form.getStatus())) {
                throw new IllegalArgumentException("已发布表单版本不可删除");
            }
            if (countActive(processModelMapper, tenantId, "form_definition_id", id) > 0) {
                throw new IllegalArgumentException("表单已被流程模型引用，不能删除");
            }
            if (countActive(formInstanceMapper, tenantId, "form_definition_id", id) > 0) {
                throw new IllegalArgumentException("表单已有实例数据，不能删除");
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<FormDefinitionVO> publish(String id, RequestContext context) {
        try {
            String tenantId = resolveTenantId(null, context);
            FormDefinition form = requireCurrent(id, tenantId, "表单定义不存在");
            if (WorkflowConstants.Status.PUBLISHED.equals(form.getStatus())) {
                return BaseResult.success(BeanUtil.toBean(form, FormDefinitionVO.class));
            }
            if (!WorkflowConstants.Status.DRAFT.equals(form.getStatus())) {
                throw new IllegalArgumentException("只有草稿表单可以发布");
            }
            validateJson(form.getSchemaJson(), "表单规则JSON", true);
            validateJson(form.getOptionJson(), "表单选项JSON", false);
            form.setStatus(WorkflowConstants.Status.PUBLISHED);
            form.setPublishedTime(LocalDateTime.now());
            fillUpdate(form, context);
            updateById(form);
            return BaseResult.success(BeanUtil.toBean(form, FormDefinitionVO.class));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("发布表单失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResult<FormDefinitionVO> copyAsDraft(String id, RequestContext context) {
        try {
            String tenantId = resolveTenantId(null, context);
            FormDefinition source = requireCurrent(id, tenantId, "表单定义不存在");
            FormDefinition draft = BeanUtil.copyProperties(source, FormDefinition.class);
            draft.setId(null);
            draft.setVersion(resolveNextVersion(source.getTenantId(), source.getFormKey()));
            draft.setStatus(WorkflowConstants.Status.DRAFT);
            draft.setPublishedTime(null);
            EntityFillUtils.fillAuditFields(draft, context, true);
            save(draft);
            return BaseResult.success(BeanUtil.toBean(draft, FormDefinitionVO.class));
        } catch (IllegalArgumentException e) {
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            return BaseResult.error("复制表单版本失败: " + e.getMessage());
        }
    }

    private void normalize(FormDefinition form) {
        form.setFormKey(trimToNull(form.getFormKey()));
        form.setFormName(trimToNull(form.getFormName()));
        if (!StringUtils.hasText(form.getStatus())) {
            form.setStatus(WorkflowConstants.Status.DRAFT);
        }
        validateIn(form.getStatus(), "表单状态不合法",
                WorkflowConstants.Status.DRAFT,
                WorkflowConstants.Status.PUBLISHED,
                WorkflowConstants.Status.DISABLED);
        if (form.getVersion() == null) {
            form.setVersion(resolveNextVersion(form.getTenantId(), form.getFormKey()));
        }
    }

    private Integer resolveNextVersion(String tenantId, String formKey) {
        if (!StringUtils.hasText(formKey)) {
            return 1;
        }
        QueryWrapper<FormDefinition> wrapper = new QueryWrapper<>();
        wrapper.select("max(version) as version")
                .eq("tenant_id", tenantId)
                .eq("form_key", formKey)
                .eq("delete_flag", 0);
        FormDefinition latest = baseMapper.selectOne(wrapper);
        return latest == null || latest.getVersion() == null ? 1 : latest.getVersion() + 1;
    }
}
