package com.lawoffice.workflow.service.impl;

import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.ProcessCategory;
import com.lawoffice.workflow.mapper.FormDefinitionMapper;
import com.lawoffice.workflow.mapper.ProcessCategoryMapper;
import com.lawoffice.workflow.mapper.ProcessModelMapper;
import com.lawoffice.workflow.service.IProcessCategoryService;
import com.lawoffice.workflow.vo.ProcessCategoryVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ProcessCategoryServiceImpl extends AbstractWorkflowConfigServiceImpl<ProcessCategoryMapper, ProcessCategory, ProcessCategoryVO> implements IProcessCategoryService {

    private final FormDefinitionMapper formDefinitionMapper;
    private final ProcessModelMapper processModelMapper;

    @Autowired
    public ProcessCategoryServiceImpl(FormDefinitionMapper formDefinitionMapper,
            ProcessModelMapper processModelMapper) {
        this.formDefinitionMapper = formDefinitionMapper;
        this.processModelMapper = processModelMapper;
    }

    @Override
    protected void doBeforeSave(BaseDTO<ProcessCategory> saveDTO) {
        ProcessCategory category = saveDTO == null ? null : saveDTO.getEntity();
        prepareTenant(category, saveDTO);
        category.setCategoryCode(trimToNull(category.getCategoryCode()));
        category.setCategoryName(trimToNull(category.getCategoryName()));
        if (!StringUtils.hasText(category.getStatus())) {
            category.setStatus(WorkflowConstants.Status.ENABLED);
        }
        if (category.getSortOrder() == null) {
            category.setSortOrder(0);
        }

        requireText(category.getCategoryCode(), "分类编码不能为空");
        requireText(category.getCategoryName(), "分类名称不能为空");
        validateIn(category.getStatus(), "分类状态不合法",
                WorkflowConstants.Status.ENABLED,
                WorkflowConstants.Status.DISABLED);
        validateUnique(category, "同一租户下分类编码不能重复",
                "category_code", category.getCategoryCode());

        if (StringUtils.hasText(category.getParentId())) {
            if (category.getParentId().equals(category.getId())) {
                throw new IllegalArgumentException("上级分类不能选择自身");
            }
            requireActiveById(baseMapper, category.getParentId(), category.getTenantId(), "上级分类不存在");
        }
    }

    @Override
    protected void doBeforeDelete(BaseDTO<ProcessCategory> deleteDTO) {
        String tenantId = resolveTenantId(null, deleteDTO.getContext());
        for (String id : resolveDeleteIds(deleteDTO)) {
            requireCurrent(id, tenantId, "流程分类不存在");
            if (countActive(baseMapper, tenantId, "parent_id", id) > 0) {
                throw new IllegalArgumentException("分类下存在子分类，不能删除");
            }
            if (countActive(formDefinitionMapper, tenantId, "category_id", id) > 0) {
                throw new IllegalArgumentException("分类下存在表单定义，不能删除");
            }
            if (countActive(processModelMapper, tenantId, "category_id", id) > 0) {
                throw new IllegalArgumentException("分类下存在流程模型，不能删除");
            }
        }
    }
}
