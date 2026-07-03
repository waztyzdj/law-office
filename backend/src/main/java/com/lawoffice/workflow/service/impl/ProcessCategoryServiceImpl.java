package com.lawoffice.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.framework.dto.BasePageDTO;
import com.lawoffice.workflow.constant.WorkflowConstants;
import com.lawoffice.workflow.entity.ProcessCategory;
import com.lawoffice.workflow.mapper.ArchiveRecordMapper;
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

    private final ArchiveRecordMapper archiveRecordMapper;
    private final FormDefinitionMapper formDefinitionMapper;
    private final ProcessModelMapper processModelMapper;

    @Autowired
    public ProcessCategoryServiceImpl(ArchiveRecordMapper archiveRecordMapper,
            FormDefinitionMapper formDefinitionMapper,
            ProcessModelMapper processModelMapper) {
        this.archiveRecordMapper = archiveRecordMapper;
        this.formDefinitionMapper = formDefinitionMapper;
        this.processModelMapper = processModelMapper;
    }

    @Override
    protected void doBeforeList(BaseDTO<ProcessCategory> baseDTO) {
        applyTenantAndDefaultSort(baseDTO);
    }

    @Override
    protected void doBeforePage(BasePageDTO<ProcessCategory> basePageDTO) {
        applyTenantAndDefaultSort(basePageDTO);
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
            if (countActive(archiveRecordMapper, tenantId, "category_id", id) > 0) {
                throw new IllegalArgumentException("分类下存在归档记录，不能删除");
            }
        }
    }

    private void applyTenantAndDefaultSort(BaseDTO<ProcessCategory> baseDTO) {
        QueryWrapper<ProcessCategory> wrapper = (QueryWrapper<ProcessCategory>) baseDTO.getQueryWrapper();
        if (wrapper == null) {
            wrapper = new QueryWrapper<>();
            baseDTO.setQueryWrapper(wrapper);
        }
        wrapper.eq("tenant_id", resolveTenantId(null, baseDTO.getContext()))
                .orderByAsc("sort_order")
                .orderByAsc("create_time");
    }
}
