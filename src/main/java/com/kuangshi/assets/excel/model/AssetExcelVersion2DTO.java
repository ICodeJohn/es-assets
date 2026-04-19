package com.kuangshi.assets.excel.model;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class AssetExcelVersion2DTO {

    @ExcelProperty("asset_id")
    private String assetId;

    @ExcelProperty("title")
    private String title;

    @ExcelProperty("uploader")
    private String uploader;

    @ExcelProperty("uploaded_at")
    private String uploadedAt;

    @ExcelProperty("file_size_bytes")
    private String fileSizeBytes;

    @ExcelProperty("status")
    private String status;

    @ExcelProperty("tags")
    private String tags;

    @ExcelProperty("city")
    private String city;

    @ExcelProperty("spend")
    private String spend;

    @ExcelProperty("platform")
    private String platform;

    @ExcelProperty("resolution")
    private String resolution;
}
