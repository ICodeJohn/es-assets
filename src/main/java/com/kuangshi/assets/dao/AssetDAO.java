package com.kuangshi.assets.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsAggregate;
import co.elastic.clients.elasticsearch._types.aggregations.StringTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.RefreshResponse;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.util.NamedValue;
import com.fasterxml.jackson.databind.ObjectMapper;
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


@Slf4j
@Service
@RequiredArgsConstructor
public class AssetService {

    private final ElasticsearchClient elasticsearchClient;
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
                            .properties("assetId", p -> p.keyword(k -> k))
                            .properties("title", p -> p.text(t -> t))
                            .properties("uploader", p -> p.keyword(k -> k))
                            .properties("timestamp", p -> p.long_(l -> l))
                            .properties("reviewStatus", p -> p.keyword(k -> k))
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

            List<UploaderAvgSizeDTO> avgSizeByUploaderResult = avgSizeByUploader();
            log.info("统计审核状态为已通过的素材中，各上传人的平均文件大小:{}", objectMapper.writeValueAsString(avgSizeByUploaderResult));

            List<TagStatsDTO> top5Tags = getTop5Tags();
            log.info("按标签统计素材数量，列出数量最多的前 5 个标签:{}", objectMapper.writeValueAsString(top5Tags));

            List<UploaderQualityDTO> uploaderQualityDTOS = analyzeUploaderQuality();
            log.info("分析各上传人的素材质量和审核通过率，识别优质内容生产者，要求审核通过数量大于0:{}", objectMapper.writeValueAsString(uploaderQualityDTOS));

        } catch (Exception e) {
            log.error("索引创建失败", e);
        }
    }

    /**
     * 统计审核状态为"已通过"的素材中，各上传人的平均文件大小
     */
    public void batchSaveOrUpdateAssets(List<AssetDocument> assetDocuments) {

        try {
            if (assetDocuments == null || assetDocuments.isEmpty()) {
                return;
            }
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
            for (AssetDocument doc : assetDocuments) {

                if (doc.getAssetId() == null || doc.getAssetId().isEmpty()) {
                    continue;
                }

                bulkBuilder.operations(op -> op
                        .index(idx -> idx
                                .index("assets")
                                .id(doc.getAssetId())
                                .document(doc)
                        )
                );
            }

            BulkResponse response = elasticsearchClient.bulk(bulkBuilder.build());



            if (response.errors()) {
                log.error("批量写入存在错误");
                response.items().forEach(item -> {
                    if (item.error() != null) {
                        log.error("写入失败 id={}, reason={}",
                                item.id(),
                                item.error().reason());
                    }
                });
            } else {
                log.info("批量保存/更新素材成功，数量: {}", response.items().size());
            }
            //快速刷新落盘
            RefreshResponse refreshResponse = elasticsearchClient.indices()
                    .refresh(r -> r.index("assets"));

            System.out.println("刷新结果: " + refreshResponse.shards());

        } catch (Exception e) {
            log.error("批量保存/更新素材失败", e);
            throw new RuntimeException("批量保存/更新素材失败", e);
        }
    }

    public List<UploaderAvgSizeDTO> avgSizeByUploader() {
        try {
            // 构建聚合查询
            SearchResponse<AssetDocument> response = elasticsearchClient.search(s -> s
                            .index("assets")
                            .size(0)  // 不返回文档，只返回聚合结果
                            .query(q -> q
                                    .term(t -> t
                                            .field("reviewStatus")
                                            .value("approved")
                                    )
                            )
                            .aggregations("group_by_uploader", a -> a
                                    .terms(t -> t
                                            .field("uploader")
                                            .size(1000)
                                    )
                                    .aggregations("avg_size", ag -> ag
                                            .avg(avg -> avg.field("size"))
                                    )
                            ),
                    AssetDocument.class
            );

            List<UploaderAvgSizeDTO> result = new ArrayList<>();

            // 获取聚合结果 - 修复版本
            if (response.aggregations() != null) {
                Aggregate groupAgg = response.aggregations().get("group_by_uploader");
                if (groupAgg != null) {
                    StringTermsAggregate termsAgg = groupAgg.sterms();
                    if (termsAgg != null && termsAgg.buckets() != null) {
                        for (StringTermsBucket bucket : termsAgg.buckets().array()) {
                            String uploader = bucket.key().stringValue();
                            // 获取平均大小子聚合
                            Aggregate avgAgg = bucket.aggregations().get("avg_size");
                            if (avgAgg != null) {
                                Double avgSizeInBytes = avgAgg.avg().value();
                                double avgSizeInMB = avgSizeInBytes / 1024 / 1024;

                                UploaderAvgSizeDTO dto = new UploaderAvgSizeDTO();
                                dto.setUploader(uploader);
                                dto.setAvgSize(String.format("%.2f MB", avgSizeInMB));

                                result.add(dto);
                                System.out.printf("上传人: %s, 平均大小: %.2f MB", uploader, avgSizeInMB);
                            }
                        }
                    }
                }
            }
            // 按平均大小降序排序
            result.sort((a, b) -> {
                double sizeA = Double.parseDouble(a.getAvgSize().replace(" MB", ""));
                double sizeB = Double.parseDouble(b.getAvgSize().replace(" MB", ""));
                return Double.compare(sizeB, sizeA);
            });

            System.out.println("共找到 " + result.size() + " 个上传人");
            return result;

        } catch (Exception e) {
            log.error("统计各上传人平均文件大小失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 按标签统计素材数量，列出数量最多的前 5 个标签
     */
    public List<TagStatsDTO> getTop5Tags() {
        try {
            SearchResponse<AssetDocument> response = elasticsearchClient.search(s -> s
                            .index("assets")
                            .size(0)
                            .aggregations("top_tags", a -> a
                                    .terms(t -> t
                                            .field("tags")
                                            .size(5)
                                            .order(List.of(
                                                    NamedValue.of("_count", SortOrder.Desc)
                                            ))
                                    )
                            ),
                    AssetDocument.class
            );

            List<TagStatsDTO> result = new ArrayList<>();

            Aggregate tagAgg = response.aggregations().get("top_tags");

            if (tagAgg != null && tagAgg.isSterms()) {
                StringTermsAggregate termsAgg = tagAgg.sterms();

                if (termsAgg.buckets() != null && termsAgg.buckets().isArray()) {
                    for (StringTermsBucket bucket : termsAgg.buckets().array()) {
                        TagStatsDTO dto = new TagStatsDTO();
                        dto.setTag(bucket.key().stringValue());
                        dto.setCount(bucket.docCount());
                        result.add(dto);
                    }
                }
            }

            return result;

        } catch (Exception e) {
            log.error("统计标签失败", e);
            return Collections.emptyList();
        }
    }

    /**
     * 业务意义：
     * 分析各上传人的素材质量和审核通过率，识别优质内容生产者，要求审核通过数量大于0
     */
    public List<UploaderQualityDTO> analyzeUploaderQuality() throws IOException {

        SearchResponse<Void> response = elasticsearchClient.search(s -> s
                        .index("assets")
                        .size(0)
                        .aggregations("by_uploader", a -> a
                                .terms(t -> t.field("uploader").size(100))

                                // ✔ 正确 approved_count
                                .aggregations("approved_count", ag -> ag
                                        .filter(f -> f
                                                .term(t -> t
                                                        .field("reviewStatus")
                                                        .value("approved")
                                                )
                                        )
                                )

                                .aggregations("avg_size", ag -> ag
                                        .avg(avg -> avg.field("size"))
                                )

                                .aggregations("last_active", ag -> ag
                                        .max(m -> m.field("timestamp"))
                                )
                        ),
                Void.class
        );

        List<UploaderQualityDTO> result = new ArrayList<>();

        Aggregate agg = response.aggregations().get("by_uploader");

        if (agg == null || agg.sterms() == null) {
            return result;
        }

        for (StringTermsBucket bucket : agg.sterms().buckets().array()) {

            String uploader = bucket.key().stringValue();
            long total = bucket.docCount();

            long approved = 0;
            Aggregate approvedAgg = bucket.aggregations().get("approved_count");
            if (approvedAgg != null && approvedAgg.filter() != null) {
                approved = approvedAgg.filter().docCount();
            }
            if(approved == 0){
                continue;
            }
            double avgSize = 0;
            Aggregate avgAgg = bucket.aggregations().get("avg_size");
            if (avgAgg != null && avgAgg.avg() != null ) {
                avgSize = avgAgg.avg().value() / 1024 / 1024;
            }

            long lastActive = 0;
            Aggregate lastAgg = bucket.aggregations().get("last_active");
            if (lastAgg != null && lastAgg.max() != null) {
                lastActive = (long) lastAgg.max().value();
            }

            double rate = total == 0 ? 0 : (double) approved / total * 100;

            UploaderQualityDTO dto = new UploaderQualityDTO();
            dto.setUploader(uploader);
            dto.setTotalCount(total);
            dto.setApprovedCount(approved);
            dto.setApprovalRate(round2(rate));
            dto.setAvgSizeInMB(round2(avgSize));
            dto.setLastActiveTime(new Date(lastActive));

            result.add(dto);
        }

        result.sort(Comparator
                .comparing(UploaderQualityDTO::getApprovalRate).reversed()
                .thenComparing(UploaderQualityDTO::getAvgSizeInMB).reversed()
                .thenComparing(UploaderQualityDTO::getLastActiveTime,
                        Comparator.nullsLast(Comparator.reverseOrder()))
        );

        return result;
    }


    /**
     * 根据字段值过滤且排序指定内容
     */
    public List<AssetDocument> searchAssets(Map<String, String> params) throws IOException {

        List<Query> mustQueries = new ArrayList<>();
        List<SortOptions> sortOptions = new ArrayList<>();

        for (Map.Entry<String, String> entry : params.entrySet()) {

            String key = entry.getKey();
            String value = entry.getValue();

            if ("sort".equals(key)) {

                // sort=uploaded_at:desc,file_size:asc
                String[] sorts = value.split(",");

                for (String s : sorts) {
                    String[] parts = s.split(":");
                    String field = parts[0];
                    String order = parts.length > 1 ? parts[1] : "asc";

                    sortOptions.add(SortOptions.of(so -> so
                            .field(f -> f
                                    .field(field)
                                    .order("desc".equalsIgnoreCase(order)
                                            ? SortOrder.Desc
                                            : SortOrder.Asc)
                            )
                    ));
                }
            }

            else if (key.contains("[") && key.contains("]")) {

                String field = key.substring(0, key.indexOf("["));
                String op = key.substring(key.indexOf("[") + 1, key.indexOf("]"));

                mustQueries.add(Query.of(q -> q
                        .range(r -> {
                            r.field(field);

                            switch (op) {
                                case "gte" -> r.gte(JsonData.of(value));
                                case "lte" -> r.lte(JsonData.of(value));
                                case "gt"  -> r.gt(JsonData.of(value));
                                case "lt"  -> r.lt(JsonData.of(value));
                            }

                            return r;
                        })
                ));
            }

            else {

                mustQueries.add(Query.of(q -> q
                        .term(t -> t
                                .field(key)
                                .value(value)
                        )
                ));
            }
        }


        Query finalQuery = mustQueries.isEmpty()
                ? Query.of(q -> q.matchAll(m -> m))
                : Query.of(q -> q.bool(b -> b.must(mustQueries)));


        SearchResponse<AssetDocument> response =
                elasticsearchClient.search(s -> s
                                .index("assets")
                                .query(finalQuery)
                                .sort(sortOptions)
                                .size(100),
                        AssetDocument.class
                );

        List<AssetDocument> result = new ArrayList<>();

        for (Hit<AssetDocument> hit : response.hits().hits()) {
            if (hit.source() != null) {
                result.add(hit.source());
            }
        }

        return result;
    }

    /**
     * 根据ID查询
     */
    public Map<String, Object> getAssetById(String id, String fields) throws IOException {
        List<String> includes = (fields != null && !fields.isEmpty())
                ? Arrays.stream(fields.split(","))
                .map(String::trim)
                .toList()
                : null;

        @SuppressWarnings("unchecked")
        GetResponse<Map<String, Object>> response =
                (GetResponse<Map<String, Object>>) (GetResponse<?>) elasticsearchClient.get(
                        g -> g.index("assets").id(id),
                        Map.class
                );
        if (!response.found()) {
            return Collections.emptyMap();
        }

        Map<String, Object> source = response.source();

        if (includes != null && !includes.isEmpty() && source != null) {
            source.keySet().retainAll(new HashSet<>(includes));
        }

        return source;
    }

    private double round2(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}
