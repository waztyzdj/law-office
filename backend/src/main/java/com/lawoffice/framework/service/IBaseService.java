package com.lawoffice.framework.service;

import com.lawoffice.framework.dto.BaseDTO;
import com.lawoffice.framework.dto.BasePageDTO;
import com.lawoffice.framework.dto.BaseResult;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.Map;

public interface IBaseService<E> {

    /**
     * 查询列表（不分页）
     */
    BaseResult<List<E>> list(BaseDTO<E> baseDTO);

    /**
     * 分页查询列表
     */
    BaseResult<Map<String, Object>> page(BasePageDTO<E> basePageDTO);

    /**
     * 根据ID查询单个实体
     */
    BaseResult<E> getById(BaseDTO<E> idDTO);

    /**
     * 保存数据（新增或修改）
     */
    BaseResult<E> save(BaseDTO<E> saveDTO);

    /**
     * 批量保存数据
     */
    BaseResult<List<E>> batchSave(BaseDTO<E> batchSaveDTO);

    /**
     * 删除单个数据
     */
    BaseResult<Void> delete(BaseDTO<E> deleteDTO);

    /**
     * 批量删除数据
     */
    BaseResult<Void> batchDelete(BaseDTO<E> deleteDTO);

    /**
     * 从 Excel 导入数据
     * @param dataList Excel 中的数据列表
     * @param importDTO 导入请求参数
     * @return 成功导入的数量
     */
    BaseResult<Integer> importExcel(List<E> dataList, BaseDTO<?> importDTO);

    /**
     * 导出数据为 Excel
     * @param response HTTP 响应对象
     * @param baseDTO 查询条件
     */
    void exportExcel(HttpServletResponse response, BaseDTO<E> baseDTO);
}
