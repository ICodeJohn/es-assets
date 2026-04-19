package com.kuangshi.assets.excel.model;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class AssetExcelVersion3DTO {

    @ExcelProperty("ID")
    private String id;

    @ExcelProperty("素材title")
    private String assetTitle;

    @ExcelProperty("上传者")
    private String uploader;

    @ExcelProperty("timestamp")
    private Long timestamp;

    @ExcelProperty("size")
    private Double size;

    @ExcelProperty("size_unit")
    private String sizeUnit;

    @ExcelProperty("review_status")
    private String reviewStatus;

    @ExcelProperty("tags")
    private String tags;  // Excel中读取为字符串，后续转换

    @ExcelProperty("城市")
    private String city;

    @ExcelProperty("duration_sec")
    private Integer durationSec;

    @ExcelProperty("投放平台")
    private String platform;
}
