package com.kuangshi.assets.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


import java.util.List;
import java.util.Map;

/**
 * 素材实体类，对应 ES 中的 assets 索引
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AssetDocument {


    private String assetId;

    private String title;

    private String uploader;

    private Long timestamp;

    private String status;

    private List<String> tags;

    private String city;

    private Long size;

    private Map<String, Object> extra;

    private String fileType;

    private Map<String, Object> source;
}
