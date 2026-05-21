package com.lawoffice.framework.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.framework.dto.BasePageDTO;
import com.lawoffice.framework.vo.BaseVO;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.entity.BaseEntity;
import com.lawoffice.framework.service.IBaseService;
import com.lawoffice.util.EntityFillUtils;
import com.lawoffice.util.ExcelUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.ParameterizedType;
import java.util.List;

/**
 * 基础服务实现类
 */
@Slf4j
public class BaseServiceImpl<M extends BaseMapper<E>, E extends BaseEntity, V extends BaseVO>
    extends ServiceImpl<M, E> implements IBaseService<E, V> {

    protected Class<V> getVoClass() {
        return (Class<V>) ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[2];
    }

    /**
     * 列表查询前处理（钩子方法，子类可重写）
     */
    protected void doBeforeList(BaseDTO<E> baseDTO) {
    }

    /**
     * 查询列表（不分页）
     */
    @Override
    public BaseResult<List<V>> list(BaseDTO<E> baseDTO) {
        try {
            doBeforeList(baseDTO);

            QueryWrapper<E> wrapper = (QueryWrapper<E>) baseDTO.getQueryWrapper();
            if (wrapper == null) {
                wrapper = new QueryWrapper<>();
            }

            wrapper.eq("delete_flag", 0);

            List<E> list = baseMapper.selectList(wrapper);
            List<V> voList = BeanUtil.copyToList(list, getVoClass());

            doAfterList(baseDTO, voList);
            return BaseResult.success(voList);
        } catch (Exception e) {
            log.error("列表查询失败", e);
            return BaseResult.error("列表查询失败: " + e.getMessage());
        }
    }

    /**
     * 列表查询后处理（钩子方法，子类可重写）
     */
    protected void doAfterList(BaseDTO<E> baseDTO, List<V> list) {
    }

    /**
     * 分页查询前处理（钩子方法，子类可重写）
     */
    protected void doBeforePage(BasePageDTO<E> basePageDTO) {
    }

    /**
     * 分页查询列表
     */
    @Override
    public BaseResult<PageVO<V>> page(BasePageDTO<E> basePageDTO) {
        try {
            doBeforePage(basePageDTO);

            QueryWrapper<E> wrapper = (QueryWrapper<E>) basePageDTO.getQueryWrapper();
            if (wrapper == null) {
                wrapper = new QueryWrapper<>();
            }

            wrapper.eq("delete_flag", 0);

            Page<E> page = new Page<>(basePageDTO.getPageNum(), basePageDTO.getPageSize());
            Page<E> resultPage = this.page(page, wrapper);

            List<V> voList = BeanUtil.copyToList(resultPage.getRecords(), getVoClass());
            PageVO<V> pageVO = new PageVO<>(voList, resultPage.getTotal(), resultPage.getCurrent(), resultPage.getSize());

            doAfterPage(basePageDTO, pageVO);
            return BaseResult.success(pageVO);
        } catch (Exception e) {
            log.error("分页查询失败", e);
            return BaseResult.error("分页查询失败: " + e.getMessage());
        }
    }

    /**
     * 分页查询后处理（钩子方法，子类可重写）
     */
    protected void doAfterPage(BasePageDTO<E> basePageDTO, PageVO<V> result) {
    }

    /**
     * 保存前处理（钩子方法，子类可重写）
     */
    protected void doBeforeSave(BaseDTO<E> saveDTO) {
    }

    /**
     * 保存数据（新增或修改）
     */
    @Override
    public BaseResult<V> save(BaseDTO<E> saveDTO) {
        try {
            doBeforeSave(saveDTO);
            E requestData = saveDTO.getEntity();
            RequestContext context = saveDTO.getContext();
            
            E entity = BeanUtil.copyProperties(requestData, getEntityClass());
            
            boolean isCreate = entity.getId() == null || entity.getId().isEmpty();
            EntityFillUtils.fillAuditFields(entity, context, isCreate);
            
            this.saveOrUpdate(entity);
            
            V vo = BeanUtil.toBean(entity, getVoClass());
            doAfterSave(saveDTO, vo);
            return BaseResult.success(vo);
        } catch (IllegalArgumentException e) {
            log.warn("保存参数校验失败: {}", e.getMessage());
            return BaseResult.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("保存失败", e);
            return BaseResult.error("保存失败: " + e.getMessage());
        }
    }

    /**
     * 保存后处理（钩子方法，子类可重写）
     */
    protected void doAfterSave(BaseDTO<E> saveDTO, V vo) {
    }

    /**
     * 根据ID查询前处理（钩子方法，子类可重写）
     */
    protected void doBeforeGetById(BaseDTO<E> idDTO) {
    }

    /**
     * 根据ID查询单个实体
     */
    @Override
    public BaseResult<V> getById(BaseDTO<E> idDTO) {
        try {
            doBeforeGetById(idDTO);

            String id = idDTO.getId();
            if (id == null || id.isEmpty()) {
                return BaseResult.error("ID不能为空");
            }

            E entity = super.getById(id);

            if (entity == null) {
                return BaseResult.error("数据不存在");
            }

            if (entity.getDeleteFlag() != null && entity.getDeleteFlag() == 1) {
                return BaseResult.error("数据已被删除");
            }

            V vo = BeanUtil.toBean(entity, getVoClass());
            doAfterGetById(idDTO, vo);
            return BaseResult.success(vo);
        } catch (Exception e) {
            log.error("根据ID查询失败", e);
            return BaseResult.error("根据ID查询失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID查询后处理（钩子方法，子类可重写）
     */
    protected void doAfterGetById(BaseDTO<E> idDTO, V vo) {
    }

    /**
     * 批量保存前处理（钩子方法，子类可重写）
     */
    protected void doBeforeBatchSave(BaseDTO<E> batchSaveDTO) {
    }

    /**
     * 批量保存数据
     */
    @Override
    public BaseResult<List<V>> batchSave(BaseDTO<E> batchSaveDTO) {
        try {
            doBeforeBatchSave(batchSaveDTO);
            
            List<E> dataList = batchSaveDTO.getEntityList();
            if (dataList == null || dataList.isEmpty()) {
                return BaseResult.error("保存数据不能为空");
            }
            
            RequestContext context = batchSaveDTO.getContext();
            List<V> savedVoList = new java.util.ArrayList<>();
            
            for (E requestData : dataList) {
                E entity = BeanUtil.copyProperties(requestData, getEntityClass());
                
                boolean isCreate = entity.getId() == null || entity.getId().isEmpty();
                EntityFillUtils.fillAuditFields(entity, context, isCreate);
                
                this.saveOrUpdate(entity);
                savedVoList.add(BeanUtil.toBean(entity, getVoClass()));
            }
            
            doAfterBatchSave(batchSaveDTO, savedVoList);
            return BaseResult.success(savedVoList);
        } catch (Exception e) {
            log.error("批量保存失败", e);
            return BaseResult.error("批量保存失败: " + e.getMessage());
        }
    }

    /**
     * 批量保存后处理（钩子方法，子类可重写）
     */
    protected void doAfterBatchSave(BaseDTO<E> batchSaveDTO, List<V> voList) {
    }

    /**
     * 删除前处理（钩子方法，子类可重写）
     */
    protected void doBeforeDelete(BaseDTO<E> deleteDTO) {
    }

    /**
     * 删除单个数据（逻辑删除）
     */
    @Override
    public BaseResult<Void> delete(BaseDTO<E> deleteDTO) {
        try {
            doBeforeDelete(deleteDTO);
            String id = deleteDTO.getId();
            if (id == null || id.isEmpty()) {
                return BaseResult.error("ID不能为空");
            }

            String deleteBy = deleteDTO.getContext() != null ?
                    deleteDTO.getContext().getUsername() : "system";

            E entity = getEntityClass().getDeclaredConstructor().newInstance();
            entity.setId(id);
            EntityFillUtils.fillDeleteFields(entity, deleteBy);

            this.updateById(entity);

            doAfterDelete(deleteDTO);
            return BaseResult.success();
        } catch (Exception e) {
            log.error("删除失败", e);
            return BaseResult.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 删除后处理（钩子方法，子类可重写）
     */
    protected void doAfterDelete(BaseDTO<E> deleteDTO) {
    }

    /**
     * 批量删除数据（逻辑删除）
     */
    @Override
    public BaseResult<Void> batchDelete(BaseDTO<E> deleteDTO) {
        try {
            doBeforeDelete(deleteDTO);
            List<String> ids = deleteDTO.getDeleteIds();
            if (ids == null || ids.isEmpty()) {
                return BaseResult.error("删除ID列表不能为空");
            }

            String deleteBy = deleteDTO.getContext() != null ?
                    deleteDTO.getContext().getUsername() : "system";

            for (String id : ids) {
                try {
                    E entity = getEntityClass().getDeclaredConstructor().newInstance();
                    entity.setId(id);
                    EntityFillUtils.fillDeleteFields(entity, deleteBy);
                    this.updateById(entity);
                } catch (Exception e) {
                    log.error("删除ID {} 失败", id, e);
                }
            }

            doAfterDelete(deleteDTO);
            return BaseResult.success();
        } catch (Exception e) {
            log.error("批量删除失败", e);
            return BaseResult.error("批量删除失败: " + e.getMessage());
        }
    }

    /**
     * 导出前处理（钩子方法，子类可重写）
     */
    protected void doBeforeExport(HttpServletResponse response, BaseDTO<E> baseDTO) {
    }

    /**
     * 导出数据为 Excel
     */
    @Override
    public void exportExcel(HttpServletResponse response, BaseDTO<E> baseDTO) {
        try {
            doBeforeExport(response, baseDTO);

            QueryWrapper<E> queryWrapper = (QueryWrapper<E>) baseDTO.getQueryWrapper();
            if (queryWrapper == null) {
                queryWrapper = new QueryWrapper<>();
            }

            queryWrapper.eq("delete_flag", 0);

            List<E> list = this.list(queryWrapper);

            ExcelUtils.export(response, list, getEntityClass());

            doAfterExport(response, baseDTO, list.size());
        } catch (Exception e) {
            log.error("导出Excel失败", e);
            throw new RuntimeException("导出Excel失败: " + e.getMessage());
        }
    }

    /**
     * 导出后处理（钩子方法，子类可重写）
     */
    protected void doAfterExport(HttpServletResponse response, BaseDTO<E> baseDTO, int count) {
    }

    /**
     * 导入前处理（钩子方法，子类可重写）
     */
    protected void doBeforeImport(List<E> dataList, BaseDTO<?> importDTO) {
    }

    /**
     * 从 Excel 导入数据
     */
    @Override
    public BaseResult<Integer> importExcel(List<E> dataList, BaseDTO<?> importDTO) {
        doBeforeImport(dataList, importDTO);

        try {
            int successCount = 0;
            RequestContext context = importDTO.getContext();

            for (E requestData : dataList) {
                try {
                    E entity = BeanUtil.copyProperties(requestData, getEntityClass());
                    boolean isCreate = entity.getId() == null || entity.getId().isEmpty();
                    EntityFillUtils.fillAuditFields(entity, context, isCreate);

                    this.saveOrUpdate(entity);
                    successCount++;
                } catch (Exception e) {
                    log.error("导入单条数据失败", e);
                }
            }

            doAfterImport(dataList, importDTO, successCount);
            return BaseResult.success(successCount);
        } catch (Exception e) {
            log.error("导入Excel失败", e);
            return BaseResult.error("导入Excel失败: " + e.getMessage());
        }
    }

    /**
     * 导入后处理（钩子方法，子类可重写）
     */
    protected void doAfterImport(List<E> dataList, BaseDTO<?> importDTO, int successCount) {
    }
}
