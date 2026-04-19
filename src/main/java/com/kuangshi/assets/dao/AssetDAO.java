package com.kuangshi.assets.dao;

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
import com.kuangshi.assets.model.AssetDocument;
import com.kuangshi.assets.model.TagStatsDTO;
import com.kuangshi.assets.model.UploaderAvgSizeDTO;
import com.kuangshi.assets.model.UploaderQualityDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;


@Slf4j
@Service
@RequiredArgsConstructor
public class AssetDAO  {

    private final ElasticsearchClient elasticsearchClient;

    /**
     * 保存或者更新素材
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
                                            .field("status")
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
                                                        .field("status")
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
                .comparing(UploaderQualityDTO::getApprovalRate, Comparator.reverseOrder())
                .thenComparing(UploaderQualityDTO::getAvgSizeInMB, Comparator.reverseOrder())
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
