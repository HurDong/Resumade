package com.resumade.api.infra.ai;

import com.resumade.api.experience.service.ExperienceAiService;
import com.resumade.api.workspace.service.ClassifierAiService;
import com.resumade.api.workspace.service.FinalEditorAiService;
import com.resumade.api.workspace.service.JdTextAiService;
import com.resumade.api.workspace.service.JdVisionAiService;
import com.resumade.api.workspace.service.OpenAiResponsesWorkspaceDraftService;
import com.resumade.api.workspace.service.SpellCheckAiService;
import com.resumade.api.workspace.service.WorkspaceDraftAiService;
import com.resumade.api.workspace.service.WorkspacePatchAiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatLanguageModel;
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

    @Value("${openai.models.final-editor:gpt-4o-mini}")
    private String finalEditorModelName;

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

    /**
     * 최종 편집기 AI — plain text 출력이 필요하므로 JSON 포맷 없이 구성
     */
    @Bean
    public FinalEditorAiService finalEditorAiService() {
        return AiServices.builder(FinalEditorAiService.class)
                .chatLanguageModel(buildTextChatModel(finalEditorModelName))
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

    // -------------------------------------------------------------------------
    // 신규: PromptStrategy 파이프라인용 빈
    // -------------------------------------------------------------------------

    /**
     * StrategyDraftGeneratorService가 사용하는 ChatLanguageModel.
     * workspaceDraftAiService와 동일한 모델을 공유하되, AiServices 래퍼 없이 직접 사용합니다.
     */
    @Bean(name = "workspaceDraftChatModel")
    public ChatLanguageModel workspaceDraftChatModel() {
        return buildChatModel(workspaceDraftModelName);
    }

    /**
     * 문항 분류 전용 소형 모델 서비스.
     * 빠른 분류를 위해 experienceModel (gpt-4o-mini)과 동일 모델을 재사용합니다.
     */
    @Bean
    public ClassifierAiService classifierAiService() {
        // 분류는 짧은 응답만 필요하므로 가장 가벼운 모델 사용
        OpenAiChatModel classifierModel = OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName(experienceModelName)   // gpt-4o-mini
                .temperature(0.0)                 // 분류는 결정론적으로
                .maxRetries(1)
                .timeout(openAiTimeout)
                .logRequests(false)
                .logResponses(false)
                .build();
        return AiServices.builder(ClassifierAiService.class)
                .chatLanguageModel(classifierModel)
                .build();
    }

    // -------------------------------------------------------------------------

    /**
     * 맞춤법 검사 전용 AI 서비스.
     * temperature=0.0 — 맞춤법은 창의성이 아닌 정확성이 기준이므로 결정론적으로 실행한다.
     * finalEditorModelName(gpt-4o-mini) 을 공유하여 별도 모델 설정 없이 재사용한다.
     */
    @Bean
    public SpellCheckAiService spellCheckAiService() {
        OpenAiChatModel spellCheckModel = OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName(finalEditorModelName)   // gpt-4o-mini
                .temperature(0.0)                  // 맞춤법 교정은 결정론적으로
                .maxRetries(1)
                .timeout(openAiTimeout)
                .responseFormat("json_object")     // Structured Output 강제
                .logRequests(false)
                .logResponses(false)
                .build();
        return AiServices.builder(SpellCheckAiService.class)
                .chatLanguageModel(spellCheckModel)
                .build();
    }

    // -------------------------------------------------------------------------

    /** plain text 응답용 (responseFormat 미설정) */
    private OpenAiChatModel buildTextChatModel(String modelName) {
        return OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName(modelName)
                .temperature(0.7)
                .maxRetries(0)
                .timeout(openAiTimeout)
                .logRequests(false)
                .logResponses(false)
                .build();
    }
}
