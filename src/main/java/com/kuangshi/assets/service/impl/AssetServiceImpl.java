package com.kuangshi.assets.service.impl;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.DynamicMapping;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kuangshi.assets.dao.AssetDAO;
import com.kuangshi.assets.excel.reader.AssetExcelReader;
import com.kuangshi.assets.model.AssetDocument;
import com.kuangshi.assets.model.TagStatsDTO;
import com.kuangshi.assets.model.UploaderAvgSizeDTO;
import com.kuangshi.assets.model.UploaderQualityDTO;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

import com.kuangshi.assets.service.AssetService;


@Slf4j
@Service
@RequiredArgsConstructor
public class AssetServiceImpl implements AssetService {

    private final ElasticsearchClient elasticsearchClient;
    private final AssetDAO assetDAO;
    private final AssetExcelReader assetExcelReader;


    @PostConstruct
    public void initIndex() {
        recreateIndex();
    }

    /**
     * 重建索引
     */
    public void recreateIndex() {
        try {

            String indexName = "assets";

            // 1. 判断索引是否存在
            boolean exists = elasticsearchClient.indices()
                    .exists(e -> e.index(indexName))
                    .value();

            if (exists) {
                log.info("删除旧索引 {}", indexName);
                elasticsearchClient.indices().delete(d -> d.index(indexName));
            }

            // 2. 创建索引（基础 settings）
            log.info("创建索引 {}", indexName);

            elasticsearchClient.indices().create(c -> c
                    .index(indexName)
                    .settings(s -> s
                            .numberOfShards("1")
                            .numberOfReplicas("0")
                    )
                    .mappings(m -> m
                            .dynamic(DynamicMapping.False)  // 禁用动态映射：忽略未知字段
                            .properties("assetId", p -> p.keyword(k -> k))
                            .properties("title", p -> p.text(t -> t))
                            .properties("uploader", p -> p.keyword(k -> k))
                            .properties("timestamp", p -> p.long_(l -> l))
                            .properties("status", p -> p.keyword(k -> k))
                            .properties("tags", p -> p.keyword(k -> k))
                            .properties("city", p -> p.keyword(k -> k))
                            .properties("size", p -> p.long_(l -> l))
                            .properties("fileType", p -> p.keyword(k -> k))
                            .properties("extra", p -> p.object(k -> k))
                            .properties("source", p -> p.object(k -> k))
                    )
            );

            log.info("索引创建成功");

            log.info("初始化数据assets-1.csv开始");
            String filepath1 = "src/main/resources/cvs/assets-1.csv";
            assetExcelReader.readCsvFileWithListenerVersion1(filepath1);
            log.info("初始化数据assets-1.csv完成");

            log.info("初始化数据assets-2.csv开始");
            String filepath2 = "src/main/resources/cvs/assets-2.csv";
            assetExcelReader.readCsvFileWithListenerVersion2(filepath2);
            log.info("初始化数据assets-2.csv完成");

            log.info("初始化数据assets-3.csv开始");
            String filepath3 = "src/main/resources/cvs/assets-3.csv";
            assetExcelReader.readCsvFileWithListenerVersion3(filepath3);
            log.info("初始化数据assets-3.csv完成");

            ObjectMapper objectMapper = new ObjectMapper();

            List<UploaderAvgSizeDTO> avgSizeByUploaderResult = assetDAO.avgSizeByUploader();
            log.info("统计审核状态为已通过的素材中，各上传人的平均文件大小:{}", objectMapper.writeValueAsString(avgSizeByUploaderResult));

            List<TagStatsDTO> top5Tags = assetDAO.getTop5Tags();
            log.info("按标签统计素材数量，列出数量最多的前 5 个标签:{}", objectMapper.writeValueAsString(top5Tags));

            List<UploaderQualityDTO> uploaderQualityDTOS = assetDAO.analyzeUploaderQuality();
            log.info("分析各上传人的素材质量和审核通过率，识别优质内容生产者，要求审核通过数量大于0:{}", objectMapper.writeValueAsString(uploaderQualityDTOS));

        } catch (Exception e) {
            log.error("索引创建失败", e);
        }
    }


    @Override
    public List<UploaderAvgSizeDTO> avgSizeByUploader() {
        return assetDAO.avgSizeByUploader();
    }

    @Override
    public List<TagStatsDTO> getTop5Tags() {
        return assetDAO.getTop5Tags();
    }

    @Override
    public List<UploaderQualityDTO> analyzeUploaderQuality() throws IOException {
        return assetDAO.analyzeUploaderQuality();
    }

    @Override
    public List<AssetDocument> searchAssets(Map<String, String> params) throws IOException {
        return assetDAO.searchAssets(params);
    }

    @Override
    public Map<String, Object> getAssetById(String id, String fields) throws IOException {
        return assetDAO.getAssetById(id, fields);

    }
}
