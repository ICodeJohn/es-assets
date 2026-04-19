package com.kuangshi.assets.model;

import lombok.Data;
import java.util.Date;

@Data
public class UploaderQualityDTO {
    private String uploader;           // 上传人
    private long totalCount;           // 总素材数
    private long approvedCount;        // 审核通过数
    private double approvalRate;       // 通过率（%）
    private double avgSizeInMB;        // 平均文件大小（MB）
    private Date lastActiveTime;       // 最近活跃时间
}