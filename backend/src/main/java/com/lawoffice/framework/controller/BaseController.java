package com.lawoffice.framework.controller;

import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.framework.dto.BasePageDTO;
import com.lawoffice.framework.dto.BaseResult;
import com.lawoffice.framework.dto.QueryParams;
import com.lawoffice.framework.entity.*;
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
 * 提供通用的 CRUD 操作接口，子类继承后可自动获得以下功能：
 * - 列表查询（不分页）
 * - 分页查询
 * - 保存数据（新增或修改）
 * - 删除数据（单个或批量）
 * - 导出 Excel
 * - 导入 Excel
 *
 * @param <S> Service 类型，需实现 IBaseService 接口
 * @param <E> 实体类型，需继承 BaseEntity
 */
@Slf4j
public class BaseController<S extends IBaseService<E>, E extends BaseEntity> {

    @Setter
    protected S baseService;

    protected Class<E> entityClass;

    protected String moduleName;

    /**
     * 构造函数，初始化实体类和模块名称
     * 通过反射获取泛型参数，并从实体类的 @ModuleInfo 注解中获取模块名称
     */
    @SuppressWarnings("unchecked")
    public BaseController() {
        Type[] types = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments();
        this.entityClass = (Class<E>) types[1];

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
    protected void doBeforeInitBaseDTO(BaseDTO<?> baseDTO, HttpServletRequest request, HttpServletResponse response) {
    }

    /**
     * 初始化 BaseDTO 的上下文信息
     * 自动设置 request、response 和 context
     *
     * @param baseDTO DTO 对象
     * @param request HTTP 请求对象
     * @param response HTTP 响应对象
     */
    protected void initBaseDTO(BaseDTO<?> baseDTO, HttpServletRequest request, HttpServletResponse response) {
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
    protected void doAfterInitBaseDTO(BaseDTO<?> baseDTO, HttpServletRequest request, HttpServletResponse response) {
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
     * @param queryParams 查询请求（包含查询条件）
     * @param httpRequest HTTP 请求对象
     * @param httpResponse HTTP 响应对象
     * @return 查询结果列表
     */
    @PostMapping("/list")
    @Operation(summary = "列表查询", description = "查询列表数据（不分页），支持动态查询条件")
    public BaseResult<List<E>> list(
            @RequestBody(required = false) QueryParams queryParams,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            BaseDTO<E> baseDTO = new BaseDTO<>();
            initBaseDTO(baseDTO, httpRequest, httpResponse);

            if (queryParams != null && queryParams.getQueryParams() != null && !queryParams.getQueryParams().isEmpty()) {
                baseDTO.setQueryWrapper(QueryWrapperBuilderUtils.build(queryParams.getQueryParams()));
            }

            doBeforeList(baseDTO, httpRequest, httpResponse);

            BaseResult<List<E>> result = baseService.list(baseDTO);

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
    protected void doAfterList(BaseDTO<E> baseDTO, BaseResult<List<E>> result, HttpServletRequest request, HttpServletResponse response) {
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
     * @param pageNum 页码
     * @param pageSize 每页数量
     * @param queryParams 查询请求（包含查询条件）
     * @param httpRequest HTTP 请求对象
     * @param httpResponse HTTP 响应对象
     * @return 查询结果，包含 records（数据列表）和 total（总数）
     */
    @PostMapping("/page")
    @Operation(summary = "分页查询", description = "分页查询列表数据，支持动态查询条件")
    public BaseResult<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestBody(required = false) QueryParams queryParams,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            BasePageDTO<E> basePageDTO = new BasePageDTO<>();
            basePageDTO.setPageNum(pageNum);
            basePageDTO.setPageSize(pageSize);

            initBaseDTO(basePageDTO, httpRequest, httpResponse);

            if (queryParams != null && queryParams.getQueryParams() != null && !queryParams.getQueryParams().isEmpty()) {
                basePageDTO.setQueryWrapper(QueryWrapperBuilderUtils.build(queryParams.getQueryParams()));
            }

            doBeforePage(basePageDTO, httpRequest, httpResponse);

            BaseResult<Map<String, Object>> result = baseService.page(basePageDTO);

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
    protected void doAfterPage(BasePageDTO<E> basePageDTO, BaseResult<Map<String, Object>> result, HttpServletRequest request, HttpServletResponse response) {
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
     * @param idDTO 包含ID的请求参数
     * @param httpRequest HTTP 请求对象
     * @param httpResponse HTTP 响应对象
     * @return 查询到的实体对象
     */
    @PostMapping("/getById")
    @Operation(summary = "根据ID查询")
    public BaseResult<E> getById(
            @RequestBody BaseDTO<E> idDTO,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            initBaseDTO(idDTO, httpRequest, httpResponse);

            doBeforeGetById(idDTO, httpRequest, httpResponse);

            BaseResult<E> result = baseService.getById(idDTO);

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
    protected void doAfterGetById(BaseDTO<E> idDTO, BaseResult<E> result, HttpServletRequest request, HttpServletResponse response) {
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
     * @param entity 实体对象
     * @param httpRequest HTTP 请求对象
     * @param httpResponse HTTP 响应对象
     * @return 保存后的实体对象
     */
    @PostMapping("/save")
    @Operation(summary = "保存数据")
    public BaseResult<E> save(
            @RequestBody E entity,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            BaseDTO<E> saveDTO = new BaseDTO<>();
            saveDTO.setEntity(entity);

            initBaseDTO(saveDTO, httpRequest, httpResponse);

            doBeforeSave(saveDTO, httpRequest, httpResponse);

            BaseResult<E> result = baseService.save(saveDTO);

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
    protected void doAfterSave(BaseDTO<E> saveDTO, BaseResult<E> result, HttpServletRequest request, HttpServletResponse response) {
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
     * @param entityList 实体列表
     * @param httpRequest HTTP 请求对象
     * @param httpResponse HTTP 响应对象
     * @return 保存后的实体列表
     */
    @PostMapping("/batchSave")
    @Operation(summary = "批量保存")
    public BaseResult<List<E>> batchSave(
            @RequestBody List<E> entityList,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            BaseDTO<E> batchSaveDTO = new BaseDTO<>();
            batchSaveDTO.setEntityList(entityList);

            initBaseDTO(batchSaveDTO, httpRequest, httpResponse);

            doBeforeBatchSave(batchSaveDTO, httpRequest, httpResponse);

            BaseResult<List<E>> result = baseService.batchSave(batchSaveDTO);

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
    protected void doAfterBatchSave(BaseDTO<E> batchSaveDTO, BaseResult<List<E>> result, HttpServletRequest request, HttpServletResponse response) {
    }

    /**
     * 删除前处理（钩子方法，子类可重写）
     *
     * @param deleteDTO 删除请求参数
     * @param request HTTP 请求对象
     * @param response HTTP 响应对象
     */
    protected void doBeforeDelete(BaseDTO<?> deleteDTO, HttpServletRequest request, HttpServletResponse response) {
    }

    /**
     * 删除单个数据（逻辑删除）
     *
     * @param entity 实体对象，从中获取ID进行删除
     * @param httpRequest HTTP 请求对象
     * @param httpResponse HTTP 响应对象
     * @return 删除结果
     */
    @PostMapping("/delete")
    @Operation(summary = "删除数据")
    public BaseResult<Void> delete(
            @RequestBody E entity,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            BaseDTO<E> deleteDTO = new BaseDTO<>();
            deleteDTO.setEntity(entity);
            if (entity != null && entity.getId() != null) {
                deleteDTO.setId(entity.getId());
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
    protected void doAfterDelete(BaseDTO<?> deleteDTO, BaseResult<Void> result, HttpServletRequest request, HttpServletResponse response) {
    }

    /**
     * 批量删除前处理（钩子方法，子类可重写）
     *
     * @param deleteDTO 删除请求参数
     * @param request HTTP 请求对象
     * @param response HTTP 响应对象
     */
    protected void doBeforeBatchDelete(BaseDTO<?> deleteDTO, HttpServletRequest request, HttpServletResponse response) {
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
    protected void doAfterBatchDelete(BaseDTO<?> deleteDTO, BaseResult<Void> result, HttpServletRequest request, HttpServletResponse response) {
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

            BaseDTO<?> importDTO = new BaseDTO<>();
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
     * @param queryParams 查询请求（包含查询条件）
     * @param httpRequest HTTP 请求对象
     * @param httpResponse HTTP 响应对象
     */
    @PostMapping("/export")
    @Operation(summary = "导出Excel", description = "导出数据为 Excel 文件，支持动态查询条件")
    public void export(
            @RequestBody(required = false) QueryParams queryParams,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            BaseDTO<E> baseDTO = new BaseDTO<>();
            initBaseDTO(baseDTO, httpRequest, httpResponse);

            if (queryParams != null && queryParams.getQueryParams() != null && !queryParams.getQueryParams().isEmpty()) {
                baseDTO.setQueryWrapper(QueryWrapperBuilderUtils.build(queryParams.getQueryParams()));
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
