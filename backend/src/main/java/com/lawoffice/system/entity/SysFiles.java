package com.lawoffice.system.entity;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.ExcelIgnore;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lawoffice.framework.entity.BaseEntity;
import com.lawoffice.framework.annotation.ModuleInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_files")
@ModuleInfo(name = "知识库文档管理", description = "系统知识库文档信息管理")
@Schema(description = "知识库文档")
public class SysFiles extends BaseEntity {

    @ExcelProperty("文件名称")
    @Schema(description = "文件名称")
    private String fileName;

    @ExcelProperty("文件地址")
    @Schema(description = "文件地址")
    private String url;

    @ExcelProperty("文档类型")
    @Schema(description = "文档类型（folder:文件夹 excel:excel doc:word ppt:ppt image:图片  archive:其他文档 video:视频 pdf:pdf）")
    private String fileType;

    @ExcelProperty("文件上传类型")
    @Schema(description = "文件上传类型(temp/本地上传(临时文件) manage/知识库)")
    private String storeType;

    @ExcelProperty("父级ID")
    @Schema(description = "父级id")
    private String parentId;

    @ExcelProperty("租户ID")
    @Schema(description = "租户id")
    private String tenantId;

    @ExcelProperty("文件大小")
    @Schema(description = "文件大小（kb）")
    private Double fileSize;

    @ExcelProperty("是否文件夹")
    @Schema(description = "是否文件夹(1：是  0：否)")
    private String izFolder;

    @ExcelProperty("是否为1级文件夹")
    @Schema(description = "是否为1级文件夹，允许为空 (1：是 )")
    private String izRootFolder;

    @ExcelProperty("是否标星")
    @Schema(description = "是否标星(1：是  0：否)")
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
    @Schema(description = "分享权限(1.关闭分享 2.允许所有联系人查看 3.允许任何人查看)")
    private String sharePerms;

    @ExcelProperty("是否允许下载")
    @Schema(description = "是否允许下载(1：是  0：否)")
    private String enableDown;

    @ExcelProperty("是否允许修改")
    @Schema(description = "是否允许修改(1：是  0：否)")
    private String enableUpdat;
}
