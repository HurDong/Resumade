package com.resumade.api.infra.ai;

import com.resumade.api.experience.service.ExperienceAiService;
import com.resumade.api.workspace.service.JdTextAiService;
import com.resumade.api.workspace.service.JdVisionAiService;
import com.resumade.api.workspace.service.OpenAiResponsesWorkspaceDraftService;
import com.resumade.api.workspace.service.WorkspaceDraftAiService;
import com.resumade.api.workspace.service.WorkspacePatchAiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Duration;

@Configuration
@Slf4j
public class AiConfig {

    @Value("${openai.api.key:demo}")
    private String openAiApiKey;

    @Value("${openai.api.timeout:PT5M}")
    private Duration openAiTimeout;

    @Value("${openai.models.experience:gpt-5-mini}")
    private String experienceModelName;

    @Value("${openai.models.workspace-draft:gpt-5-mini}")
    private String workspaceDraftModelName;

    @Value("${openai.models.workspace-patch:gpt-5-mini}")
    private String workspacePatchModelName;

    @Value("${openai.models.jd-text:gpt-5-mini}")
    private String jdTextModelName;

    @Value("${openai.models.jd-vision:gpt-4o}")
    private String jdVisionModelName;

    @PostConstruct
    public void logSelectedModels() {
        log.info("OpenAI models - experience: {}, workspace-draft: {}, workspace-patch: {}, jd-text: {}, jd-vision: {}",
                experienceModelName, workspaceDraftModelName, workspacePatchModelName, jdTextModelName, jdVisionModelName);
    }

    @Bean
    public ExperienceAiService experienceAiService() {
        return AiServices.builder(ExperienceAiService.class)
                .chatLanguageModel(buildChatModel(experienceModelName))
                .build();
    }

    @Bean(name = "legacyWorkspaceDraftAiService")
    public WorkspaceDraftAiService legacyWorkspaceDraftAiService() {
        return AiServices.builder(WorkspaceDraftAiService.class)
                .chatLanguageModel(buildChatModel(workspaceDraftModelName))
                .build();
    }

    @Bean
    @Primary
    public WorkspaceDraftAiService workspaceDraftAiService(
            ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Qualifier("legacyWorkspaceDraftAiService")
            WorkspaceDraftAiService legacyWorkspaceDraftAiService
    ) {
        return new OpenAiResponsesWorkspaceDraftService(
                openAiApiKey,
                workspaceDraftModelName,
                openAiTimeout,
                objectMapper,
                legacyWorkspaceDraftAiService
        );
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
                .maxRetries(0)
                .timeout(openAiTimeout)
                .responseFormat("json_object")
                .logRequests(false)
                .logResponses(false)
                .build();
    }
}
