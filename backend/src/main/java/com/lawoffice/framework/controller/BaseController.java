package com.lawoffice.framework.controller;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.framework.dto.BasePageDTO;
import com.lawoffice.framework.dto.BaseResult;
import com.lawoffice.framework.dto.RequestContext;
import com.lawoffice.framework.entity.*;
import com.lawoffice.framework.service.IBaseService;
import com.lawoffice.framework.annotation.ModuleInfo;
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
     * 获取当前登录用户名
     * 
     * @param request HTTP 请求对象
     * @return 用户名，如果未获取到则返回 "anonymous"
     */
    protected String getCurrentUsername(HttpServletRequest request) {
        Object username = request.getAttribute("username");
        return username != null ? username.toString() : "anonymous";
    }

    /**
     * 构建请求上下文信息
     * 
     * @param request HTTP 请求对象
     * @return 请求上下文对象
     */
    protected RequestContext buildContext(HttpServletRequest request) {
        return RequestContext.builder()
                .username(getCurrentUsername(request))
                .token(request.getHeader("Authorization"))
                .ipAddress(request.getRemoteAddr())
                .userAgent(request.getHeader("User-Agent"))
                .build();
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
     * 将驼峰命名转换为蛇形命名
     * 
     * @param camelCase 驼峰命名字符串
     * @return 蛇形命名字符串
     */
    protected String camelToSnake(String camelCase) {
        if (camelCase == null || camelCase.isEmpty()) {
            return camelCase;
        }
        
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    result.append('_');
                }
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * 根据 queryParams 构建 QueryWrapper
     * 自动将驼峰命名转换为蛇形命名，并支持多种查询操作符和排序
     * 
     * @param queryParams 查询参数Map（驼峰命名）
     * @return QueryWrapper
     */
    @SuppressWarnings("unchecked")
    protected QueryWrapper<E> buildQueryWrapper(Map<String, Object> queryParams) {
        QueryWrapper<E> wrapper = new QueryWrapper<>();
        
        if (queryParams == null || queryParams.isEmpty()) {
            return wrapper;
        }
        
        String orderByField = null;
        String orderDirection = null;
        
        for (Map.Entry<String, Object> entry : queryParams.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (value == null || (value instanceof String && ((String) value).isEmpty())) {
                continue;
            }
            
            if ("_orderBy".equals(key)) {
                orderByField = value.toString();
                continue;
            }
            
            if ("_orderDirection".equals(key)) {
                orderDirection = value.toString();
                continue;
            }
            
            if (key.endsWith("_like")) {
                String field = camelToSnake(key.substring(0, key.length() - 5));
                wrapper.like(field, value);
            } else if (key.endsWith("_eq")) {
                String field = camelToSnake(key.substring(0, key.length() - 3));
                wrapper.eq(field, value);
            } else if (key.endsWith("_ne")) {
                String field = camelToSnake(key.substring(0, key.length() - 3));
                wrapper.ne(field, value);
            } else if (key.endsWith("_gt")) {
                String field = camelToSnake(key.substring(0, key.length() - 3));
                wrapper.gt(field, value);
            } else if (key.endsWith("_ge")) {
                String field = camelToSnake(key.substring(0, key.length() - 3));
                wrapper.ge(field, value);
            } else if (key.endsWith("_lt")) {
                String field = camelToSnake(key.substring(0, key.length() - 3));
                wrapper.lt(field, value);
            } else if (key.endsWith("_le")) {
                String field = camelToSnake(key.substring(0, key.length() - 3));
                wrapper.le(field, value);
            } else if (key.endsWith("_in")) {
                String field = camelToSnake(key.substring(0, key.length() - 3));
                if (value instanceof List) {
                    wrapper.in(field, (List<?>) value);
                }
            } else if (key.endsWith("_between")) {
                String field = camelToSnake(key.substring(0, key.length() - 8));
                if (value instanceof List && ((List<?>) value).size() == 2) {
                    List<?> values = (List<?>) value;
                    wrapper.between(field, values.get(0), values.get(1));
                }
            }
        }
        
        // 应用排序
        applyOrderBy(wrapper, orderByField, orderDirection);
        
        return wrapper;
    }

    /**
     * 应用排序条件到 QueryWrapper
     * 使用 MyBatis-Plus 的 orderBy 方法，而不是 last()
     * 
     * @param wrapper QueryWrapper
     * @param orderByField 排序字段（多个字段用逗号分隔，驼峰命名）
     * @param orderDirection 排序方向（ASC/DESC，多个方向用逗号分隔）
     */
    protected void applyOrderBy(QueryWrapper<E> wrapper, String orderByField, String orderDirection) {
        if (orderByField == null || orderByField.isEmpty()) {
            return;
        }
        
        String[] fields = orderByField.split(",");
        String[] directions = orderDirection != null ? orderDirection.split(",") : new String[]{"ASC"};
        
        for (int i = 0; i < fields.length; i++) {
            String field = camelToSnake(fields[i].trim());
            String direction = i < directions.length ? directions[i].trim().toUpperCase() : "ASC";
            
            if (!"ASC".equals(direction) && !"DESC".equals(direction)) {
                direction = "ASC";
            }
            
            if ("DESC".equals(direction)) {
                wrapper.orderByDesc(field);
            } else {
                wrapper.orderByAsc(field);
            }
        }
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
                    baseDTO.setContext(buildContext(request));
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
     * 查询列表（不分页）
     * 
     * @param queryParams 查询条件Map（驼峰命名）
     * @param httpRequest HTTP 请求对象
     * @param httpResponse HTTP 响应对象
     * @return 查询结果列表
     */
    @PostMapping("/list")
    @Operation(summary = "列表查询", description = "查询{moduleName}列表（不分页）")
    public BaseResult<List<E>> list(
            @RequestBody(required = false) Map<String, Object> queryParams,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            BaseDTO<E> baseDTO = new BaseDTO<>();
            initBaseDTO(baseDTO, httpRequest, httpResponse);
            
            if (queryParams != null && !queryParams.isEmpty()) {
                baseDTO.setQueryWrapper(buildQueryWrapper(queryParams));
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
     * @param queryParams 查询条件Map（驼峰命名）
     * @param httpRequest HTTP 请求对象
     * @param httpResponse HTTP 响应对象
     * @return 查询结果，包含 records（数据列表）和 total（总数）
     */
    @PostMapping("/page")
    @Operation(summary = "分页查询", description = "分页查询{moduleName}列表")
    public BaseResult<Map<String, Object>> page(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestBody(required = false) Map<String, Object> queryParams,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            BasePageDTO<E> basePageDTO = new BasePageDTO<>();
            basePageDTO.setPageNum(pageNum);
            basePageDTO.setPageSize(pageSize);
            
            initBaseDTO(basePageDTO, httpRequest, httpResponse);
            
            if (queryParams != null && !queryParams.isEmpty()) {
                basePageDTO.setQueryWrapper(buildQueryWrapper(queryParams));
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
     * 从 BasePageDTO 的 orderBy 字段应用排序
     * 支持多字段排序，字段和方向用逗号分隔
     * 
     * @param wrapper QueryWrapper
     * @param orderBy 排序字段（多个字段用逗号分隔，驼峰命名）
     * @param orderDirection 排序方向（ASC/DESC，多个方向用逗号分隔）
     */
    protected void applyOrderByFromPageDTO(QueryWrapper<E> wrapper, String orderBy, String orderDirection) {
        if (orderBy == null || orderBy.isEmpty()) {
            return;
        }
        
        String[] fields = orderBy.split(",");
        String[] directions = orderDirection != null ? orderDirection.split(",") : new String[]{"ASC"};
        
        StringBuilder orderByClause = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            String field = camelToSnake(fields[i].trim());
            String direction = i < directions.length ? directions[i].trim().toUpperCase() : "ASC";
            
            if (!"ASC".equals(direction) && !"DESC".equals(direction)) {
                direction = "ASC";
            }
            
            if (i > 0) {
                orderByClause.append(", ");
            }
            orderByClause.append(field).append(" ").append(direction);
        }
        
        if (orderByClause.length() > 0) {
            wrapper.last("ORDER BY " + orderByClause);
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
    @Operation(summary = "根据ID查询", description = "根据ID查询单个{moduleName}")
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
    @Operation(summary = "保存数据", description = "新增或修改{moduleName}")
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
    @Operation(summary = "批量保存", description = "批量保存{moduleName}数据")
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
    @Operation(summary = "删除数据", description = "删除单个{moduleName}")
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
     * 批量删除数据（逻辑删除）
     * 
     * @param ids ID列表
     * @param httpRequest HTTP 请求对象
     * @param httpResponse HTTP 响应对象
     * @return 删除结果
     */
    @PostMapping("/batchDelete")
    @Operation(summary = "批量删除", description = "批量删除{moduleName}")
    public BaseResult<Void> batchDelete(
            @RequestBody List<String> ids,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            BaseDTO<E> deleteDTO = new BaseDTO<>();
            deleteDTO.setDeleteIds(ids);
            
            initBaseDTO(deleteDTO, httpRequest, httpResponse);
            
            doBeforeDelete(deleteDTO, httpRequest, httpResponse);
            
            BaseResult<Void> result = baseService.batchDelete(deleteDTO);
            
            doAfterDelete(deleteDTO, result, httpRequest, httpResponse);
            return result;
        } catch (Exception e) {
            log.error("批量删除失败", e);
            return BaseResult.error("批量删除失败: " + e.getMessage());
        }
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
    @Operation(summary = "导入Excel", description = "从Excel导入{moduleName}数据")
    public BaseResult<Integer> importExcel(
            @RequestParam("file") MultipartFile file,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            doBeforeImport(file, httpRequest, httpResponse);
            
            if (file.isEmpty()) {
                return BaseResult.error("上传文件不能为空");
            }
            
            Map<Integer, String> headMap = readExcelHeader(file);
            List<Map<Integer, String>> dataList = readExcelData(file);
            List<E> entities = convertMapsToEntities(dataList, headMap);
            
            log.info("Excel导入：读取{}行数据，成功转换{}条记录", dataList.size(), entities.size());
            
            BaseDTO<?> importDTO = new BaseDTO<>();
            importDTO.setContext(buildContext(httpRequest));
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
     * 读取Excel表头（第一行）
     */
    private Map<Integer, String> readExcelHeader(MultipartFile file) {
        try {
            List<Map<Integer, String>> allData = EasyExcel.read(file.getInputStream())
                    .sheet()
                    .headRowNumber(0)
                    .doReadSync();
            
            return allData.isEmpty() ? new java.util.HashMap<>() : allData.get(0);
        } catch (Exception e) {
            log.error("读取Excel表头失败", e);
            throw new RuntimeException("读取Excel表头失败: " + e.getMessage());
        }
    }

    /**
     * 读取Excel数据（跳过表头）
     */
    private List<Map<Integer, String>> readExcelData(MultipartFile file) {
        try {
            return EasyExcel.read(file.getInputStream())
                    .sheet()
                    .headRowNumber(1)
                    .doReadSync();
        } catch (Exception e) {
            log.error("读取Excel数据失败", e);
            throw new RuntimeException("读取Excel数据失败: " + e.getMessage());
        }
    }

    /**
     * 将Map数据转换为实体列表
     */
    private List<E> convertMapsToEntities(List<Map<Integer, String>> dataList, Map<Integer, String> headMap) {
        List<E> result = new java.util.ArrayList<>();
        
        if (dataList == null || dataList.isEmpty()) {
            return result;
        }
        
        Map<Integer, java.lang.reflect.Field> columnFieldMap = buildColumnFieldMap(headMap);
        
        for (Map<Integer, String> row : dataList) {
            try {
                E entity = createEntityFromRow(row, columnFieldMap);
                if (entity != null) {
                    result.add(entity);
                }
            } catch (Exception e) {
                log.error("转换行数据失败，跳过该行", e);
            }
        }
        
        return result;
    }

    /**
     * 从单行数据创建实体对象
     */
    private E createEntityFromRow(Map<Integer, String> row, Map<Integer, java.lang.reflect.Field> columnFieldMap) {
        try {
            E entity = entityClass.getDeclaredConstructor().newInstance();
            
            for (Map.Entry<Integer, java.lang.reflect.Field> entry : columnFieldMap.entrySet()) {
                String value = row.get(entry.getKey());
                if (value != null && !value.trim().isEmpty()) {
                    setFieldValue(entity, entry.getValue(), value.trim());
                }
            }
            
            return entity;
        } catch (Exception e) {
            log.error("创建实体对象失败", e);
            return null;
        }
    }

    /**
     * 构建列索引到字段的映射
     * 支持：@ExcelProperty注解值、字段名（英文）、模糊匹配
     */
    private Map<Integer, java.lang.reflect.Field> buildColumnFieldMap(Map<Integer, String> headMap) {
        Map<Integer, java.lang.reflect.Field> columnFieldMap = new java.util.HashMap<>();
        Map<String, java.lang.reflect.Field> fieldLookupMap = buildFieldLookupMap();
        
        for (Map.Entry<Integer, String> entry : headMap.entrySet()) {
            String headerName = entry.getValue();
            if (headerName == null || headerName.trim().isEmpty()) {
                continue;
            }
            
            java.lang.reflect.Field field = matchField(headerName, fieldLookupMap);
            if (field != null) {
                columnFieldMap.put(entry.getKey(), field);
            } else {
                log.warn("列[{}]未匹配到任何字段", headerName);
            }
        }
        
        return columnFieldMap;
    }

    /**
     * 构建字段查找表
     */
    private Map<String, java.lang.reflect.Field> buildFieldLookupMap() {
        Map<String, java.lang.reflect.Field> lookupMap = new java.util.HashMap<>();
        
        getAllFields(entityClass).forEach(field -> {
            field.setAccessible(true);
            
            String fieldNameLower = field.getName().toLowerCase();
            lookupMap.put(fieldNameLower, field);
            
            if (field.isAnnotationPresent(com.alibaba.excel.annotation.ExcelProperty.class)) {
                com.alibaba.excel.annotation.ExcelProperty excelProperty = 
                    field.getAnnotation(com.alibaba.excel.annotation.ExcelProperty.class);
                for (String name : excelProperty.value()) {
                    lookupMap.put(name.toLowerCase(), field);
                }
            }
        });
        
        return lookupMap;
    }

    /**
     * 匹配字段（优先精确匹配，其次模糊匹配）
     */
    private java.lang.reflect.Field matchField(String headerName, Map<String, java.lang.reflect.Field> lookupMap) {
        String headerLower = headerName.toLowerCase().trim();
        
        java.lang.reflect.Field field = lookupMap.get(headerLower);
        
        if (field == null) {
            String normalizedHeader = normalizeFieldName(headerLower);
            for (Map.Entry<String, java.lang.reflect.Field> entry : lookupMap.entrySet()) {
                if (normalizeFieldName(entry.getKey()).equals(normalizedHeader)) {
                    field = entry.getValue();
                    break;
                }
            }
        }
        
        return field;
    }

    /**
     * 获取类的所有字段（包括父类）
     */
    private java.util.List<java.lang.reflect.Field> getAllFields(Class<?> clazz) {
        java.util.List<java.lang.reflect.Field> fields = new java.util.ArrayList<>();
        
        Class<?> currentClass = clazz;
        while (currentClass != null && currentClass != Object.class) {
            for (java.lang.reflect.Field field : currentClass.getDeclaredFields()) {
                fields.add(field);
            }
            currentClass = currentClass.getSuperclass();
        }
        
        return fields;
    }

    /**
     * 标准化字段名（去除特殊字符）
     */
    private String normalizeFieldName(String fieldName) {
        if (fieldName == null) {
            return "";
        }
        return fieldName.toLowerCase()
                .replace("_", "")
                .replace("-", "")
                .replace(" ", "")
                .trim();
    }

    /**
     * 设置字段值（支持常见类型自动转换）
     */
    private void setFieldValue(Object entity, java.lang.reflect.Field field, String value) {
        try {
            Class<?> fieldType = field.getType();
            
            if (fieldType.equals(String.class)) {
                field.set(entity, value);
            } else if (fieldType.equals(Integer.class) || fieldType.equals(int.class)) {
                field.set(entity, Integer.parseInt(value));
            } else if (fieldType.equals(Long.class) || fieldType.equals(long.class)) {
                field.set(entity, Long.parseLong(value));
            } else if (fieldType.equals(Double.class) || fieldType.equals(double.class)) {
                field.set(entity, Double.parseDouble(value));
            } else if (fieldType.equals(Float.class) || fieldType.equals(float.class)) {
                field.set(entity, Float.parseFloat(value));
            } else if (fieldType.equals(Boolean.class) || fieldType.equals(boolean.class)) {
                field.set(entity, Boolean.parseBoolean(value));
            } else {
                field.set(entity, value);
            }
        } catch (Exception e) {
            log.debug("字段[{}]设置值[{}]失败: {}", field.getName(), value, e.getMessage());
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
     * @param queryParams 查询条件Map（驼峰命名）
     * @param httpRequest HTTP 请求对象
     * @param httpResponse HTTP 响应对象
     */
    @PostMapping("/export")
    @Operation(summary = "导出Excel", description = "导出{moduleName}数据")
    public void export(
            @RequestBody(required = false) Map<String, Object> queryParams,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        try {
            BaseDTO<E> baseDTO = new BaseDTO<>();
            initBaseDTO(baseDTO, httpRequest, httpResponse);
            
            if (queryParams != null && !queryParams.isEmpty()) {
                baseDTO.setQueryWrapper(buildQueryWrapper(queryParams));
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
