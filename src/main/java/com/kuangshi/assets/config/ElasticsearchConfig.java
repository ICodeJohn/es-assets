package com.kuangshi.assets.config;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.jspecify.annotations.NonNull;

@Configuration
public class ElasticsearchConfig extends ElasticsearchConfigurationSupport {

    @Value("${spring.elasticsearch.uris}")
    private String elasticsearchUrl;

    // 新版 ES 客户端配置
    @Override
    public ClientConfiguration clientConfiguration() {
        return ClientConfiguration.builder()
                .connectedTo(elasticsearchUrl)
                .build();
    }

    // 核心：创建 ElasticsearchTemplate
    public ElasticsearchOperations elasticsearchOperations() {
        return new ElasticsearchTemplate(ElasticsearchClients.create(clientConfiguration()));
    }
}