package com.lawoffice.framework.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.framework.dto.BasePageDTO;
import com.lawoffice.framework.dto.BaseResult;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.entity.BaseEntity;
import com.lawoffice.framework.service.IBaseService;
import com.lawoffice.framework.util.EntityFillUtils;
import com.lawoffice.framework.util.ExcelUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基础服务实现类
 * 继承 MyBatis Plus 的 ServiceImpl，提供通用的 CRUD 操作实现
 * 子类继承后可自动获得以下功能：
 * - MyBatis Plus 的所有 CRUD 方法（save, saveBatch, getById, list, page 等）
 * - 列表查询（不分页）
 * - 分页查询
 * - 根据ID查询
 * - 保存数据（新增或修改，带审计字段填充）
 * - 批量保存
 * - 删除数据（逻辑删除）
 * - 批量删除
 * - 导出 Excel
 * - 导入 Excel
 *
 * @param <M> Mapper 类型
 * @param <E> 实体类型，需继承 BaseEntity
 */
@Slf4j
public class BaseServiceImpl<M extends BaseMapper<E>, E extends BaseEntity>
    extends ServiceImpl<M, E> implements IBaseService<E> {

    /**
     * 列表查询前处理（钩子方法，子类可重写）
     *
     * @param baseDTO 请求参数
     */
    protected void doBeforeList(BaseDTO<E> baseDTO) {
    }

    /**
     * 查询列表（不分页）
     *
     * @param baseDTO 请求参数，包含 queryWrapper
     * @return 查询结果列表
     */
    @Override
    @SuppressWarnings("unchecked")
    public BaseResult<List<E>> list(BaseDTO<E> baseDTO) {
        try {
            doBeforeList(baseDTO);

            QueryWrapper<E> wrapper = (QueryWrapper<E>) baseDTO.getQueryWrapper();
            if (wrapper == null) {
                wrapper = new QueryWrapper<>();
            }

            wrapper.eq("delete_flag", 0);

            // 使用 ServiceImpl 提供的 baseMapper
            List<E> list = baseMapper.selectList(wrapper);

            doAfterList(baseDTO, list);
            return BaseResult.success(list);
        } catch (Exception e) {
            log.error("列表查询失败", e);
            return BaseResult.error("列表查询失败: " + e.getMessage());
        }
    }

    /**
     * 列表查询后处理（钩子方法，子类可重写）
     *
     * @param baseDTO 请求参数
     * @param list 查询结果列表
     */
    protected void doAfterList(BaseDTO<E> baseDTO, List<E> list) {
    }

    /**
     * 分页查询前处理（钩子方法，子类可重写）
     *
     * @param basePageDTO 分页请求参数
     */
    protected void doBeforePage(BasePageDTO<E> basePageDTO) {
    }

    /**
     * 分页查询列表
     *
     * @param basePageDTO 分页请求参数
     * @return 查询结果，包含 records 和 total
     */
    @Override
    @SuppressWarnings("unchecked")
    public BaseResult<Map<String, Object>> page(BasePageDTO<E> basePageDTO) {
        try {
            doBeforePage(basePageDTO);

            QueryWrapper<E> wrapper = (QueryWrapper<E>) basePageDTO.getQueryWrapper();
            if (wrapper == null) {
                wrapper = new QueryWrapper<>();
            }

            wrapper.eq("delete_flag", 0);

            // 使用 ServiceImpl 提供的 page 方法
            Page<E> page = new Page<>(basePageDTO.getPageNum(), basePageDTO.getPageSize());
            Page<E> resultPage = this.page(page, wrapper);

            Map<String, Object> data = new HashMap<>();
            data.put("records", resultPage.getRecords());
            data.put("total", resultPage.getTotal());

            doAfterPage(basePageDTO, data);
            return BaseResult.success(data);
        } catch (Exception e) {
            log.error("分页查询失败", e);
            return BaseResult.error("分页查询失败: " + e.getMessage());
        }
    }

    /**
     * 分页查询后处理（钩子方法，子类可重写）
     *
     * @param basePageDTO 分页请求参数
     * @param data 查询结果数据
     */
    protected void doAfterPage(BasePageDTO<E> basePageDTO, Map<String, Object> data) {
    }

    /**
     * 保存前处理（钩子方法，子类可重写）
     *
     * @param saveDTO 保存请求参数
     */
    protected void doBeforeSave(BaseDTO<E> saveDTO) {
    }

    /**
     * 保存数据（新增或修改）
     *
     * @param saveDTO 保存请求参数，包含数据和上下文信息
     * @return 保存后的实体对象
     */
    @Override
    public BaseResult<E> save(BaseDTO<E> saveDTO) {
        try {
            doBeforeSave(saveDTO);
            E requestData = saveDTO.getEntity();
            RequestContext context = saveDTO.getContext();
            
            // 使用 ServiceImpl 提供的 getEntityClass() 方法
            E entity = BeanUtil.copyProperties(requestData, getEntityClass());
            
            boolean isCreate = entity.getId() == null || entity.getId().isEmpty();
            EntityFillUtils.fillAuditFields(entity, context, isCreate);
            
            // 使用 ServiceImpl 提供的 saveOrUpdate 方法
            this.saveOrUpdate(entity);
            
            doAfterSave(saveDTO, entity);
            return BaseResult.success(entity);
        } catch (Exception e) {
            log.error("保存失败", e);
            return BaseResult.error("保存失败: " + e.getMessage());
        }
    }

    /**
     * 保存后处理（钩子方法，子类可重写）
     *
     * @param saveDTO 保存请求参数
     * @param entity 保存后的实体对象
     */
    protected void doAfterSave(BaseDTO<E> saveDTO, E entity) {
    }

    /**
     * 根据ID查询前处理（钩子方法，子类可重写）
     *
     * @param idDTO 包含ID的请求参数
     */
    protected void doBeforeGetById(BaseDTO<E> idDTO) {
    }

    /**
     * 根据ID查询单个实体
     *
     * @param idDTO 包含ID的请求参数
     * @return 查询到的实体对象
     */
    @Override
    public BaseResult<E> getById(BaseDTO<E> idDTO) {
        try {
            doBeforeGetById(idDTO);

            String id = idDTO.getId();
            if (id == null || id.isEmpty()) {
                return BaseResult.error("ID不能为空");
            }

            // 使用 ServiceImpl 提供的 getById 方法
            E entity = this.getById(id);

            if (entity == null) {
                return BaseResult.error("数据不存在");
            }

            if (entity.getDeleteFlag() != null && entity.getDeleteFlag() == 1) {
                return BaseResult.error("数据已被删除");
            }

            doAfterGetById(idDTO, entity);
            return BaseResult.success(entity);
        } catch (Exception e) {
            log.error("根据ID查询失败", e);
            return BaseResult.error("根据ID查询失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID查询后处理（钩子方法，子类可重写）
     *
     * @param idDTO 包含ID的请求参数
     * @param entity 查询到的实体对象
     */
    protected void doAfterGetById(BaseDTO<E> idDTO, E entity) {
    }

    /**
     * 批量保存前处理（钩子方法，子类可重写）
     *
     * @param batchSaveDTO 批量保存请求参数
     */
    protected void doBeforeBatchSave(BaseDTO<E> batchSaveDTO) {
    }

    /**
     * 批量保存数据
     *
     * @param batchSaveDTO 批量保存请求参数，包含实体列表和上下文信息
     * @return 保存后的实体列表
     */
    @Override
    public BaseResult<List<E>> batchSave(BaseDTO<E> batchSaveDTO) {
        try {
            doBeforeBatchSave(batchSaveDTO);
            
            List<E> dataList = batchSaveDTO.getEntityList();
            if (dataList == null || dataList.isEmpty()) {
                return BaseResult.error("保存数据不能为空");
            }
            
            RequestContext context = batchSaveDTO.getContext();
            List<E> savedEntities = new java.util.ArrayList<>();
            
            for (E requestData : dataList) {
                // 使用 ServiceImpl 提供的 getEntityClass() 方法
                E entity = BeanUtil.copyProperties(requestData, getEntityClass());
                
                boolean isCreate = entity.getId() == null || entity.getId().isEmpty();
                EntityFillUtils.fillAuditFields(entity, context, isCreate);
                
                // 使用 ServiceImpl 提供的 saveOrUpdate 方法
                this.saveOrUpdate(entity);
                
                savedEntities.add(entity);
            }
            
            doAfterBatchSave(batchSaveDTO, savedEntities);
            return BaseResult.success(savedEntities);
        } catch (Exception e) {
            log.error("批量保存失败", e);
            return BaseResult.error("批量保存失败: " + e.getMessage());
        }
    }

    /**
     * 批量保存后处理（钩子方法，子类可重写）
     *
     * @param batchSaveDTO 批量保存请求参数
     * @param entities 保存后的实体列表
     */
    protected void doAfterBatchSave(BaseDTO<E> batchSaveDTO, List<E> entities) {
    }

    /**
     * 删除前处理（钩子方法，子类可重写）
     *
     * @param deleteDTO 删除请求参数
     */
    protected void doBeforeDelete(BaseDTO<E> deleteDTO) {
    }

    /**
     * 删除单个数据（逻辑删除）
     *
     * @param deleteDTO 删除请求参数，包含ID和上下文信息
     * @return 删除结果
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

            // 使用 ServiceImpl 提供的 getEntityClass() 方法
            E entity = getEntityClass().getDeclaredConstructor().newInstance();
            entity.setId(id);
            EntityFillUtils.fillDeleteFields(entity, deleteBy);

            // 使用 ServiceImpl 提供的 updateById 方法
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
     *
     * @param deleteDTO 删除请求参数
     */
    protected void doAfterDelete(BaseDTO<E> deleteDTO) {
    }

    /**
     * 批量删除数据（逻辑删除）
     *
     * @param deleteDTO 批量删除请求参数，包含ID列表和上下文信息
     * @return 删除结果
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
                    // 使用 ServiceImpl 提供的 getEntityClass() 方法
                    E entity = getEntityClass().getDeclaredConstructor().newInstance();
                    entity.setId(id);
                    EntityFillUtils.fillDeleteFields(entity, deleteBy);
                    // 使用 ServiceImpl 提供的 updateById 方法
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
     *
     * @param response HTTP 响应对象
     * @param baseDTO 查询条件
     */
    protected void doBeforeExport(HttpServletResponse response, BaseDTO<E> baseDTO) {
    }

    /**
     * 导出数据为 Excel
     *
     * @param response HTTP 响应对象
     * @param baseDTO 查询条件
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

            // 使用 ServiceImpl 提供的 list 方法
            List<E> list = this.list(queryWrapper);

            // 使用 ServiceImpl 提供的 getEntityClass() 方法
            ExcelUtils.export(response, list, getEntityClass());

            doAfterExport(response, baseDTO, list.size());
        } catch (Exception e) {
            log.error("导出Excel失败", e);
            throw new RuntimeException("导出Excel失败: " + e.getMessage());
        }
    }

    /**
     * 导出后处理（钩子方法，子类可重写）
     *
     * @param response HTTP 响应对象
     * @param baseDTO 查询条件
     * @param count 导出数量
     */
    protected void doAfterExport(HttpServletResponse response, BaseDTO<E> baseDTO, int count) {
    }

    /**
     * 导入前处理（钩子方法，子类可重写）
     *
     * @param dataList 导入的数据列表
     * @param importDTO 导入请求参数
     */
    protected void doBeforeImport(List<E> dataList, BaseDTO<?> importDTO) {
    }

    /**
     * 从 Excel 导入数据
     *
     * @param dataList Excel 中的数据列表
     * @param importDTO 导入请求参数
     * @return 成功导入的数量
     */
    @Override
    public BaseResult<Integer> importExcel(List<E> dataList, BaseDTO<?> importDTO) {
        doBeforeImport(dataList, importDTO);

        try {
            int successCount = 0;
            RequestContext context = importDTO.getContext();

            for (E requestData : dataList) {
                try {
                    // 使用 ServiceImpl 提供的 getEntityClass() 方法
                    E entity = BeanUtil.copyProperties(requestData, getEntityClass());
                    boolean isCreate = entity.getId() == null || entity.getId().isEmpty();
                    EntityFillUtils.fillAuditFields(entity, context, isCreate);

                    // 使用 ServiceImpl 提供的 saveOrUpdate 方法
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
     *
     * @param dataList 导入的数据列表
     * @param importDTO 导入请求参数
     * @param successCount 成功导入数量
     */
    protected void doAfterImport(List<E> dataList, BaseDTO<?> importDTO, int successCount) {
    }
}
