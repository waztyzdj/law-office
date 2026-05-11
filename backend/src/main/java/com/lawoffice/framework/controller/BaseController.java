package com.lawoffice.framework.controller;

import cn.hutool.core.bean.BeanUtil;
import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.framework.dto.BasePageDTO;
import com.lawoffice.framework.result.BaseResult;
import com.lawoffice.framework.req.BasePageReq;
import com.lawoffice.framework.req.BaseQueryReq;
import com.lawoffice.framework.req.BaseReq;
import com.lawoffice.framework.vo.BaseVO;
import com.lawoffice.framework.vo.PageVO;
import com.lawoffice.framework.entity.BaseEntity;
import com.lawoffice.framework.service.IBaseService;
import com.lawoffice.framework.annotation.ModuleInfo;
import com.lawoffice.util.ExcelUtils;
import com.lawoffice.framework.util.QueryWrapperBuilderUtils;
import com.lawoffice.framework.util.RequestContextUtils;
import io.swagger.v3.oas.annotations.Operation;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * 基础控制器
 *
 * @param <S> Service 类型，需实现 IBaseService 接口
 * @param <E> 实体类型，需继承 BaseEntity
 * @param <V> VO 类型，需继承 BaseVO
 */
@Slf4j
public class BaseController<S extends IBaseService<E, V>, E extends BaseEntity, V extends BaseVO> {

    @Setter
    protected S baseService;

    protected Class<E> entityClass;
    protected Class<V> voClass;
    protected String moduleName;

    /**
     * 构造函数，初始化实体类和模块名称
     * 通过反射获取泛型参数，并从实体类的 @ModuleInfo 注解中获取模块名称
     */
    @SuppressWarnings("unchecked")
    public BaseController() {
        Type[] types = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments();
        this.entityClass = (Class<E>) types[1];
        this.voClass = (Class<V>) types[2];

        ModuleInfo moduleInfo = entityClass.getAnnotation(ModuleInfo.class);
        if (moduleInfo != null) {
            this.moduleName = moduleInfo.name();
        } else {
            this.moduleName = entityClass.getSimpleName();
        }
    }

    /**
     * 初始化 BaseDTO 前处理（钩子方法，子类可重写）
     *
     * @param baseDTO DTO 对象
     * @param request HTTP 请求对象
     * @param response HTTP 响应对象
     */
    protected void doBeforeInitBaseDTO(BaseDTO<E> baseDTO, HttpServletRequest request, HttpServletResponse response) {
    }

    /**
     * 初始化 BaseDTO 的上下文信息
     * 自动设置 request、response 和 context
     *
     * @param baseDTO DTO 对象
     * @param request HTTP 请求对象
     * @param response HTTP 响应对象
     */
    protected void initBaseDTO(BaseDTO<E> baseDTO, HttpServletRequest request, HttpServletResponse response) {
        try {
            doBeforeInitBaseDTO(baseDTO, request, response);

            if (baseDTO != null) {
                baseDTO.setRequest(request);
                baseDTO.setResponse(response);
                if (baseDTO.getContext() == null) {
                    baseDTO.setContext(RequestContextUtils.buildContext(request));
                }
            }

            doAfterInitBaseDTO(baseDTO, request, response);
        } catch (Exception e) {
            log.error("初始化 BaseDTO 失败", e);
        }
    }

    /**
     * 初始化 BaseDTO 后处理（钩子方法，子类可重写）
     *
     * @param baseDTO DTO 对象
     * @param request HTTP 请求对象
     * @param response HTTP 响应对象
     */
    protected void doAfterInitBaseDTO(BaseDTO<E> baseDTO, HttpServletRequest request, HttpServletResponse response) {
    }

    /**
     * 列表查询前处理（钩子方法，子类可重写）
     *
     * @param baseDTO 请求参数
     * @param request HTTP 请求对象
     * @param response HTTP 响应对象
     */
    protected void doBeforeList(BaseDTO<E> baseDTO, HttpServletRequest request, HttpServletResponse response) {
    }

    /**
     * 列表查询（不分页）
     *
     * @param req 查询请求
     * @param httpRequest HTTP 请求对象
     * @param httpResponse HTTP 响应对象
     * @return 查询结果列表
     */
    @PostMapping("/list")
    @Operation(summary = "列表查询", description = "查询列表数据（不分页），支持动态查询条件")
    public BaseResult<List<V>> list(
            @RequestBody(required = false) BaseQueryReq req,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            BaseDTO<E> baseDTO = new BaseDTO<>();
            initBaseDTO(baseDTO, httpRequest, httpResponse);

            // 根据 req 中的条件构建 QueryWrapper
            if (req != null && req.getQueryParams() != null && !req.getQueryParams().isEmpty()) {
                baseDTO.setQueryWrapper(QueryWrapperBuilderUtils.build(req.getQueryParams()));
            }

            doBeforeList(baseDTO, httpRequest, httpResponse);

            BaseResult<List<V>> result = baseService.list(baseDTO);

            doAfterList(baseDTO, result, httpRequest, httpResponse);
            return result;
        } catch (Exception e) {
            log.error("列表查询失败", e);
            return BaseResult.error("列表查询失败: " + e.getMessage());
        }
    }

    /**
     * 列表查询后处理（钩子方法，子类可重写）
     *
     * @param baseDTO 请求参数
     * @param result 查询结果
     * @param request HTTP 请求对象
     * @param response HTTP 响应对象
     */
    protected void doAfterList(BaseDTO<E> baseDTO, BaseResult<List<V>> result, HttpServletRequest request, HttpServletResponse response) {
    }

    /**
     * 分页查询前处理（钩子方法，子类可重写）
     *
     * @param basePageDTO 分页请求参数
     * @param request HTTP 请求对象
     * @param response HTTP 响应对象
     */
    protected void doBeforePage(BasePageDTO<E> basePageDTO, HttpServletRequest request, HttpServletResponse response) {
    }

    /**
     * 分页查询列表
     *
     * @param req 分页请求
     * @param httpRequest HTTP 请求对象
     * @param httpResponse HTTP 响应对象
     * @return 查询结果，包含 records（数据列表）和 total（总数）
     */
    @PostMapping("/page")
    @Operation(summary = "分页查询", description = "分页查询列表数据，支持动态查询条件")
    public BaseResult<PageVO<V>> page(
            @RequestBody(required = false) BasePageReq req,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            BasePageDTO<E> basePageDTO = new BasePageDTO<>();
            if (req != null) {
                basePageDTO.setPageNum(req.getPageNum());
                basePageDTO.setPageSize(req.getPageSize());
                // 根据 req 中的条件构建 QueryWrapper
                if (req.getQueryParams() != null && !req.getQueryParams().isEmpty()) {
                    basePageDTO.setQueryWrapper(QueryWrapperBuilderUtils.build(req.getQueryParams()));
                }
            }

            initBaseDTO(basePageDTO, httpRequest, httpResponse);

            doBeforePage(basePageDTO, httpRequest, httpResponse);

            BaseResult<PageVO<V>> result = baseService.page(basePageDTO);

            doAfterPage(basePageDTO, result, httpRequest, httpResponse);
            return result;
        } catch (Exception e) {
            log.error("分页查询失败", e);
            return BaseResult.error("分页查询失败: " + e.getMessage());
        }
    }

    /**
     * 分页查询后处理（钩子方法，子类可重写）
     *
     * @param basePageDTO 分页请求参数
     * @param result 查询结果
     * @param request HTTP 请求对象
     * @param response HTTP 响应对象
     */
    protected void doAfterPage(BasePageDTO<E> basePageDTO, BaseResult<PageVO<V>> result, HttpServletRequest request, HttpServletResponse response) {
    }

    /**
     * 根据ID查询前处理（钩子方法，子类可重写）
     *
     * @param idDTO 包含ID的请求参数
     * @param request HTTP 请求对象
     * @param response HTTP 响应对象
     */
    protected void doBeforeGetById(BaseDTO<E> idDTO, HttpServletRequest request, HttpServletResponse response) {
    }

    /**
     * 根据ID查询单个实体
     *
     * @param req 包含ID的请求参数
     * @param httpRequest HTTP 请求对象
     * @param httpResponse HTTP 响应对象
     * @return 查询到的实体对象
     */
    @PostMapping("/getById")
    @Operation(summary = "根据ID查询")
    public BaseResult<V> getById(
            @RequestBody BaseReq req,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            BaseDTO<E> idDTO = new BaseDTO<>();
            if (req != null) {
                idDTO.setId(req.getId());
            }
            initBaseDTO(idDTO, httpRequest, httpResponse);

            doBeforeGetById(idDTO, httpRequest, httpResponse);

            BaseResult<V> result = baseService.getById(idDTO);

            doAfterGetById(idDTO, result, httpRequest, httpResponse);
            return result;
        } catch (Exception e) {
            log.error("根据ID查询失败", e);
            return BaseResult.error("根据ID查询失败: " + e.getMessage());
        }
    }

    /**
     * 根据ID查询后处理（钩子方法，子类可重写）
     *
     * @param idDTO 包含ID的请求参数
     * @param result 查询结果
     * @param request HTTP 请求对象
     * @param response HTTP 响应对象
     */
    protected void doAfterGetById(BaseDTO<E> idDTO, BaseResult<V> result, HttpServletRequest request, HttpServletResponse response) {
    }

    /**
     * 保存前处理（钩子方法，子类可重写）
     *
     * @param saveDTO 保存请求参数
     * @param request HTTP 请求对象
     * @param response HTTP 响应对象
     */
    protected void doBeforeSave(BaseDTO<E> saveDTO, HttpServletRequest request, HttpServletResponse response) {
    }

    /**
     * 保存数据（新增或修改）
     * 根据实体 ID 判断是新增还是修改：ID 为空则新增，否则修改
     *
     * @param req 请求参数
     * @param httpRequest HTTP 请求对象
     * @param httpResponse HTTP 响应对象
     * @return 保存后的实体对象
     */
    @PostMapping("/save")
    @Operation(summary = "保存数据")
    public BaseResult<V> save(
            @RequestBody BaseReq req,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            BaseDTO<E> saveDTO = new BaseDTO<>();
            // 将 Req 转换为 Entity
            E entity = BeanUtil.copyProperties(req, entityClass);
            saveDTO.setEntity(entity);

            initBaseDTO(saveDTO, httpRequest, httpResponse);

            doBeforeSave(saveDTO, httpRequest, httpResponse);

            BaseResult<V> result = baseService.save(saveDTO);

            doAfterSave(saveDTO, result, httpRequest, httpResponse);
            return result;
        } catch (Exception e) {
            log.error("保存失败", e);
            return BaseResult.error("保存失败: " + e.getMessage());
        }
    }

    /**
     * 保存后处理（钩子方法，子类可重写）
     *
     * @param saveDTO 保存请求参数
     * @param result 保存结果
     * @param request HTTP 请求对象
     * @param response HTTP 响应对象
     */
    protected void doAfterSave(BaseDTO<E> saveDTO, BaseResult<V> result, HttpServletRequest request, HttpServletResponse response) {
    }

    /**
     * 批量保存前处理（钩子方法，子类可重写）
     *
     * @param batchSaveDTO 批量保存请求参数
     * @param request HTTP 请求对象
     * @param response HTTP 响应对象
     */
    protected void doBeforeBatchSave(BaseDTO<E> batchSaveDTO, HttpServletRequest request, HttpServletResponse response) {
    }

    /**
     * 批量保存数据
     *
     * @param reqList 请求列表
     * @param httpRequest HTTP 请求对象
     * @param httpResponse HTTP 响应对象
     * @return 保存后的实体列表
     */
    @PostMapping("/batchSave")
    @Operation(summary = "批量保存")
    public BaseResult<java.util.List<V>> batchSave(
            @RequestBody java.util.List<BaseReq> reqList,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            BaseDTO<E> batchSaveDTO = new BaseDTO<>();
            // 将 Req 列表转换为 Entity 列表
            java.util.List<E> entityList = reqList.stream()
                    .map(req -> BeanUtil.copyProperties(req, entityClass))
                    .collect(java.util.stream.Collectors.toList());
            batchSaveDTO.setEntityList(entityList);

            initBaseDTO(batchSaveDTO, httpRequest, httpResponse);

            doBeforeBatchSave(batchSaveDTO, httpRequest, httpResponse);

            BaseResult<List<V>> result = baseService.batchSave(batchSaveDTO);

            doAfterBatchSave(batchSaveDTO, result, httpRequest, httpResponse);
            return result;
        } catch (Exception e) {
            log.error("批量保存失败", e);
            return BaseResult.error("批量保存失败: " + e.getMessage());
        }
    }

    /**
     * 批量保存后处理（钩子方法，子类可重写）
     *
     * @param batchSaveDTO 批量保存请求参数
     * @param result 保存结果
     * @param request HTTP 请求对象
     * @param response HTTP 响应对象
     */
    protected void doAfterBatchSave(BaseDTO<E> batchSaveDTO, BaseResult<java.util.List<V>> result, HttpServletRequest request, HttpServletResponse response) {
    }

    /**
     * 删除前处理（钩子方法，子类可重写）
     *
     * @param deleteDTO 删除请求参数
     * @param request HTTP 请求对象
     * @param response HTTP 响应对象
     */
    protected void doBeforeDelete(BaseDTO<E> deleteDTO, HttpServletRequest request, HttpServletResponse response) {
    }

    /**
     * 删除单个数据（逻辑删除）
     *
     * @param req 请求参数，从中获取ID进行删除
     * @param httpRequest HTTP 请求对象
     * @param httpResponse HTTP 响应对象
     * @return 删除结果
     */
    @PostMapping("/delete")
    @Operation(summary = "删除数据")
    public BaseResult<Void> delete(
            @RequestBody BaseReq req,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            BaseDTO<E> deleteDTO = new BaseDTO<>();
            if (req != null) {
                deleteDTO.setId(req.getId());
            }

            initBaseDTO(deleteDTO, httpRequest, httpResponse);

            doBeforeDelete(deleteDTO, httpRequest, httpResponse);

            BaseResult<Void> result = baseService.delete(deleteDTO);

            doAfterDelete(deleteDTO, result, httpRequest, httpResponse);
            return result;
        } catch (Exception e) {
            log.error("删除失败", e);
            return BaseResult.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 删除后处理（钩子方法，子类可重写）
     *
     * @param deleteDTO 删除请求参数
     * @param result 删除结果
     * @param request HTTP 请求对象
     * @param response HTTP 响应对象
     */
    protected void doAfterDelete(BaseDTO<E> deleteDTO, BaseResult<Void> result, HttpServletRequest request, HttpServletResponse response) {
    }

    /**
     * 批量删除前处理（钩子方法，子类可重写）
     *
     * @param deleteDTO 删除请求参数
     * @param request HTTP 请求对象
     * @param response HTTP 响应对象
     */
    protected void doBeforeBatchDelete(BaseDTO<E> deleteDTO, HttpServletRequest request, HttpServletResponse response) {
    }

    /**
     * 批量删除数据（逻辑删除）
     *
     * @param ids ID列表
     * @param httpRequest HTTP 请求对象
     * @param httpResponse HTTP 响应对象
     * @return 删除结果
     */
    @PostMapping("/batchDelete")
    @Operation(summary = "批量删除")
    public BaseResult<Void> batchDelete(
            @RequestBody List<String> ids,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            BaseDTO<E> deleteDTO = new BaseDTO<>();
            deleteDTO.setDeleteIds(ids);

            initBaseDTO(deleteDTO, httpRequest, httpResponse);

            doBeforeBatchDelete(deleteDTO, httpRequest, httpResponse);

            BaseResult<Void> result = baseService.batchDelete(deleteDTO);

            doAfterBatchDelete(deleteDTO, result, httpRequest, httpResponse);
            return result;
        } catch (Exception e) {
            log.error("批量删除失败", e);
            return BaseResult.error("批量删除失败: " + e.getMessage());
        }
    }

    /**
     * 批量删除后处理（钩子方法，子类可重写）
     *
     * @param deleteDTO 删除请求参数
     * @param result 删除结果
     * @param request HTTP 请求对象
     * @param response HTTP 响应对象
     */
    protected void doAfterBatchDelete(BaseDTO<E> deleteDTO, BaseResult<Void> result, HttpServletRequest request, HttpServletResponse response) {
    }

    /**
     * 导入前处理（钩子方法，子类可重写）
     *
     * @param file Excel 文件
     * @param request HTTP 请求对象
     * @param response HTTP 响应对象
     */
    protected void doBeforeImport(MultipartFile file, HttpServletRequest request, HttpServletResponse response) {
    }

    /**
     * 导入 Excel
     *
     * @param file Excel 文件
     * @param httpRequest HTTP 请求对象
     * @param httpResponse HTTP 响应对象
     * @return 成功导入的数量
     */
    @PostMapping("/import")
    @Operation(summary = "导入Excel")
    public BaseResult<Integer> importExcel(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            doBeforeImport(file, httpRequest, httpResponse);

            if (file.isEmpty()) {
                return BaseResult.error("上传文件不能为空");
            }

            Map<Integer, String> headMap = ExcelUtils.readHeader(file);
            List<Map<Integer, String>> dataList = ExcelUtils.readData(file);
            List<E> entities = ExcelUtils.convertToEntities(dataList, headMap, entityClass);

            log.info("Excel导入：读取{}行数据，成功转换{}条记录", dataList.size(), entities.size());

            BaseDTO<E> importDTO = new BaseDTO<>();
            importDTO.setContext(RequestContextUtils.buildContext(httpRequest));
            importDTO.setRequest(httpRequest);
            importDTO.setResponse(httpResponse);

            BaseResult<Integer> result = baseService.importExcel(entities, importDTO);

            doAfterImport(file, result, httpRequest, httpResponse);
            return result;
        } catch (Exception e) {
            log.error("导入失败", e);
            return BaseResult.error("导入失败: " + e.getMessage());
        }
    }

    /**
     * 导入后处理（钩子方法，子类可重写）
     *
     * @param file Excel 文件
     * @param result 导入结果
     * @param request HTTP 请求对象
     * @param response HTTP 响应对象
     */
    protected void doAfterImport(MultipartFile file, BaseResult<Integer> result, HttpServletRequest request, HttpServletResponse response) {
    }

    /**
     * 导出前处理（钩子方法，子类可重写）
     *
     * @param baseDTO 查询条件
     * @param request HTTP 请求对象
     * @param response HTTP 响应对象
     */
    protected void doBeforeExport(BaseDTO<E> baseDTO, HttpServletRequest request, HttpServletResponse response) {
    }

    /**
     * 导出 Excel
     *
     * @param req 查询请求
     * @param httpRequest HTTP 请求对象
     * @param httpResponse HTTP 响应对象
     */
    @PostMapping("/export")
    @Operation(summary = "导出Excel", description = "导出数据为 Excel 文件，支持动态查询条件")
    public void export(
            @RequestBody(required = false) BaseQueryReq req,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            BaseDTO<E> baseDTO = new BaseDTO<>();
            initBaseDTO(baseDTO, httpRequest, httpResponse);

            // 根据 req 中的条件构建 QueryWrapper
            if (req != null && req.getQueryParams() != null && !req.getQueryParams().isEmpty()) {
                baseDTO.setQueryWrapper(QueryWrapperBuilderUtils.build(req.getQueryParams()));
            }

            doBeforeExport(baseDTO, httpRequest, httpResponse);

            baseService.exportExcel(httpResponse, baseDTO);

            doAfterExport(baseDTO, httpRequest, httpResponse);
        } catch (Exception e) {
            log.error("导出失败", e);
            try {
                httpResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                httpResponse.getWriter().write("导出失败: " + e.getMessage());
            } catch (IOException ex) {
                log.error("写入错误响应失败", ex);
            }
        }
    }

    /**
     * 导出后处理（钩子方法，子类可重写）
     *
     * @param baseDTO 查询条件
     * @param request HTTP 请求对象
     * @param response HTTP 响应对象
     */
    protected void doAfterExport(BaseDTO<E> baseDTO, HttpServletRequest request, HttpServletResponse response) {
    }

}
