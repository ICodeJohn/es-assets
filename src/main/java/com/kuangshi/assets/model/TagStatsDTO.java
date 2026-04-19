package com.kuangshi.assets.model;

import lombok.Data;

@Data
public class TagStatsDTO {
    private String tag;      // 标签名称
    private Long count;      // 出现次数
}
