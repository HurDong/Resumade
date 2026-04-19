package com.resumade.api.infra.elasticsearch;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.ClientConfiguration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchConfiguration;
import org.springframework.data.elasticsearch.support.HttpHeaders;

@Configuration
public class ElasticsearchConfig extends ElasticsearchConfiguration {

    @Value("${spring.elasticsearch.uris:http://localhost:9200}")
    private String elasticsearchUri;

    @Override
    public ClientConfiguration clientConfiguration() {
        String host = elasticsearchUri.replace("http://", "").replace("https://", "");
        return ClientConfiguration.builder()
                .connectedTo(host)
                .withDefaultHeaders(new HttpHeaders() {{
                    add("Content-Type", "application/json");
                    add("Accept", "application/json");
                }})
                .build();
    }
}
