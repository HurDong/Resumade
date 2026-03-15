package com.resumade.api.infra.ai;

import com.resumade.api.experience.service.ExperienceAiService;
import com.resumade.api.workspace.service.WorkspaceAiService;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class AiConfig {

    @Value("${openai.api.key:demo}")
    private String openAiApiKey;

    @Bean
    public OpenAiChatModel openAiChatModel() {
        return OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName("gpt-5.4")
                .timeout(Duration.ofSeconds(60))
                .responseFormat("json_object")
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Bean
    public ExperienceAiService experienceAiService(OpenAiChatModel chatModel) {
        return AiServices.builder(ExperienceAiService.class)
                .chatLanguageModel(chatModel)
                .build();
    }

    @Bean
    public WorkspaceAiService workspaceAiService(OpenAiChatModel chatModel) {
        return AiServices.builder(WorkspaceAiService.class)
                .chatLanguageModel(chatModel)
                .build();
    }
}
