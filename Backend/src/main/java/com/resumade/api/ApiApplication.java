package com.resumade.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = {
		"com.resumade.api.coding.domain",
		"com.resumade.api.experience.domain",
		"com.resumade.api.profile.domain",
		"com.resumade.api.recruit.domain",
		"com.resumade.api.technote.domain",
		"com.resumade.api.workspace.domain"
})
@EnableElasticsearchRepositories(basePackages = "com.resumade.api.experience.document")
@EnableAsync
public class ApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(ApiApplication.class, args);
	}

}
