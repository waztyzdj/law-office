package com.lawoffice.system.vo;

import com.lawoffice.framework.vo.BaseVO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文件视图对象
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysFilesVO extends BaseVO {
    private static final long serialVersionUID = 1L;

    /**
     * 文件名称
     */
    private String fileName;

    /**
     * 文件地址
     */
    private String url;

    /**
     * 文件大小
     */
    private Long fileSize;

    /**
     * 文件类型
     */
    private String fileType;

    /**
     * 存储类型
     */
    private String storageType;

    /**
     * 模块
     */
    private String module;

    /**
     * 描述
     */
    private String description;

    /**
     * 删除标识(0-正常,1-已删除)
     */
    private Integer deleteFlag;
}
