package com.kuangshi.assets.excel.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class AssetExcelVersion1DTO {

    @ExcelProperty("素材编号")
    private String assetId;

    @ExcelProperty("标题")
    private String title;

    @ExcelProperty("上传人")
    private String uploader;

    @ExcelProperty("上传日期")
    private String uploadDate;

    @ExcelProperty("文件大小(MB)")
    private String size;

    @ExcelProperty("审核状态")
    private String reviewStatus;

    @ExcelProperty("标签")
    private String tags;

    @ExcelProperty("审核人")
    private String reviewer;

    @ExcelProperty("所在城市")
    private String city;

    @ExcelProperty("备注")
    private String remark;
}
