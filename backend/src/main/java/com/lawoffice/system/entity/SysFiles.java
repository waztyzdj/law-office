package com.lawoffice.system.entity;

import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.entity.BaseTenantEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_files")
@Schema(description = "文件元数据")
public class SysFiles extends BaseTenantEntity {

    @ExcelProperty("文件名称")
    @Schema(description = "文件名称")
    private String fileName;

    @ExcelProperty("文件地址")
    @Schema(description = "文件地址")
    private String url;

    @ExcelProperty("文件类型")
    @Schema(description = "文件类型")
    private String fileType;

    @ExcelProperty("存储类型")
    @Schema(description = "存储类型")
    private String storeType;

    @ExcelProperty("父级ID")
    @Schema(description = "父级ID")
    private String parentId;

    @ExcelProperty("文件大小")
    @Schema(description = "文件大小")
    private Double fileSize;

    @ExcelProperty("是否文件夹")
    @Schema(description = "是否文件夹")
    private String izFolder;

    @ExcelProperty("是否一级文件夹")
    @Schema(description = "是否一级文件夹")
    private String izRootFolder;

    @ExcelProperty("是否收藏")
    @Schema(description = "是否收藏")
    private String izStar;

    @ExcelProperty("下载次数")
    @Schema(description = "下载次数")
    private Integer downCount;

    @ExcelProperty("阅读次数")
    @Schema(description = "阅读次数")
    private Integer readCount;

    @ExcelProperty("分享链接")
    @Schema(description = "分享链接")
    private String shareUrl;

    @ExcelProperty("分享权限")
    @Schema(description = "分享权限")
    private String sharePerms;

    @ExcelProperty("允许下载")
    @Schema(description = "允许下载")
    private String enableDown;

    @ExcelProperty("允许修改")
    @Schema(description = "允许修改")
    private String enableUpdat;

    @ExcelIgnore
    @TableField(exist = false)
    private String relationId;
}
