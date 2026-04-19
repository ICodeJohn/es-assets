package com.kuangshi.assets.model;

import lombok.Data;

@Data
public class UploaderAvgSizeDTO {

    /**
     * 上传人名称
     */
    private String uploader;

    /**
     * 平均文件大小（单位：MB）
     */
    private String avgSize;
}
