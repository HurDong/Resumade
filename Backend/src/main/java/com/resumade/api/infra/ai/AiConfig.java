package com.resumade.api.infra.ai;

import com.resumade.api.experience.service.ExperienceAiService;
import com.resumade.api.workspace.service.JdTextAiService;
import com.resumade.api.workspace.service.JdVisionAiService;
import com.resumade.api.workspace.service.WorkspaceDraftAiService;
import com.resumade.api.workspace.service.WorkspacePatchAiService;
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

    @Value("${openai.models.experience:gpt-5-mini}")
    private String experienceModelName;

    @Value("${openai.models.workspace-draft:gpt-4o}")
    private String workspaceDraftModelName;

    @Value("${openai.models.workspace-patch:gpt-5.2}")
    private String workspacePatchModelName;

    @Value("${openai.models.jd-text:gpt-5-mini}")
    private String jdTextModelName;

    @Value("${openai.models.jd-vision:gpt-4o}")
    private String jdVisionModelName;

    @Bean
    public ExperienceAiService experienceAiService() {
        return AiServices.builder(ExperienceAiService.class)
                .chatLanguageModel(buildChatModel(experienceModelName))
                .build();
    }

    @Bean
    public WorkspaceDraftAiService workspaceDraftAiService() {
        return AiServices.builder(WorkspaceDraftAiService.class)
                .chatLanguageModel(buildChatModel(workspaceDraftModelName))
                .build();
    }

    @Bean
    public WorkspacePatchAiService workspacePatchAiService() {
        return AiServices.builder(WorkspacePatchAiService.class)
                .chatLanguageModel(buildChatModel(workspacePatchModelName))
                .build();
    }

    @Bean
    public JdTextAiService jdTextAiService() {
        return AiServices.builder(JdTextAiService.class)
                .chatLanguageModel(buildChatModel(jdTextModelName))
                .build();
    }

    @Bean
    public JdVisionAiService jdVisionAiService() {
        return AiServices.builder(JdVisionAiService.class)
                .chatLanguageModel(buildChatModel(jdVisionModelName))
                .build();
    }

    private OpenAiChatModel buildChatModel(String modelName) {
        return OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName(modelName)
                .temperature(1.0)
                .timeout(Duration.ofSeconds(180))
                .responseFormat("json_object")
                .logRequests(true)
                .logResponses(true)
                .build();
    }
}
