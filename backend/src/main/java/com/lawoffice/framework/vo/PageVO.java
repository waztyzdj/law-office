package com.lawoffice.framework.vo;

import lombok.Data;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 通用分页结果对象
 *
 * @param <T> 具体的业务 VO 类型
 */
@Data
public class PageVO<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 当前页码
     */
    private long pageNum;

    /**
     * 每页数量
     */
    private long pageSize;

    /**
     * 总记录数
     */
    private long total;

    /**
     * 总页数
     */
    private long pages;

    /**
     * 数据列表
     */
    private List<T> records;

    public PageVO() {
    }

    public PageVO(List<T> records, long total, long pageNum, long pageSize) {
        this.records = records;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.pages = (total + pageSize - 1) / pageSize;
    }

    /**
     * 空列表构造器
     */
    public static <T> PageVO<T> empty(long pageNum, long pageSize) {
        return new PageVO<>(Collections.emptyList(), 0L, pageNum, pageSize);
    }
}
