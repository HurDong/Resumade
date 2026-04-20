package com.resumade.api.infra.ai;

import com.resumade.api.experience.service.ExperienceAiService;
import com.resumade.api.workspace.service.ClassifierAiService;
import com.resumade.api.workspace.service.IntentExtractorAiService;
import com.resumade.api.workspace.service.QuestionAnalyzerAiService;
import com.resumade.api.workspace.service.DraftQualityCheckerAiService;
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

    @Value("${openai.models.spell-check:gpt-5-nano}")
    private String spellCheckModelName;

    @Value("${openai.models.question-analyzer:gpt-4o-mini}")
    private String questionAnalyzerModelName;

    @Value("${openai.models.draft-quality-checker:gpt-4o-mini}")
    private String draftQualityCheckerModelName;

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
        OpenAiChatModel classifierModel = OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName(experienceModelName)   // gpt-4o-mini
                .temperature(0.0)
                .maxRetries(1)
                .timeout(openAiTimeout)
                .logRequests(false)
                .logResponses(false)
                .build();
        return AiServices.builder(ClassifierAiService.class)
                .chatLanguageModel(classifierModel)
                .build();
    }

    /**
     * 복합 문항 세부 intent 추출 서비스.
     * 분류기와 동일한 경량 모델을 사용합니다.
     */
    @Bean
    public IntentExtractorAiService intentExtractorAiService() {
        OpenAiChatModel intentModel = OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName(experienceModelName)   // gpt-4o-mini
                .temperature(0.0)
                .maxRetries(1)
                .timeout(openAiTimeout)
                // JSON 배열 반환이므로 json_object 미사용 — sanitizeJsonArray로 파싱
                .logRequests(false)
                .logResponses(false)
                .build();
        return AiServices.builder(IntentExtractorAiService.class)
                .chatLanguageModel(intentModel)
                .build();
    }

    // -------------------------------------------------------------------------
    // v2 파이프라인 빈
    // -------------------------------------------------------------------------

    /**
     * 문항 심층 분석 서비스 (v2). 분류 + 복합 감지 + 전략 방향을 한 번의 LLM 호출로 처리.
     */
    @Bean
    public QuestionAnalyzerAiService questionAnalyzerAiService() {
        OpenAiChatModel model = OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName(questionAnalyzerModelName)
                .temperature(0.0)
                .maxRetries(1)
                .timeout(openAiTimeout)
                .responseFormat("json_object")
                .logRequests(false)
                .logResponses(false)
                .build();
        return AiServices.builder(QuestionAnalyzerAiService.class)
                .chatLanguageModel(model)
                .build();
    }

    /**
     * 초안 품질 검수 서비스 (v2 Tier-2). requiredElements 충족 여부 확인.
     */
    @Bean
    public DraftQualityCheckerAiService draftQualityCheckerAiService() {
        OpenAiChatModel model = OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName(draftQualityCheckerModelName)
                .temperature(0.0)
                .maxRetries(1)
                .timeout(openAiTimeout)
                .responseFormat("json_object")
                .logRequests(false)
                .logResponses(false)
                .build();
        return AiServices.builder(DraftQualityCheckerAiService.class)
                .chatLanguageModel(model)
                .build();
    }

    // -------------------------------------------------------------------------

    /**
     * 맞춤법 검사 전용 AI 서비스.
     * gpt-5-nano 전용 모델 사용 — 단어·조사·어미 수준의 규칙 기반 검사에 최적화.
     * temperature=1.0 고정 — gpt-5-nano는 1.0만 지원 (기본값 0.7 자동 전송 방지).
     */
    @Bean
    public SpellCheckAiService spellCheckAiService() {
        OpenAiChatModel spellCheckModel = OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName(spellCheckModelName)
                .temperature(1.0)                  // gpt-5-nano는 1.0만 지원
                .maxRetries(1)
                .timeout(openAiTimeout)
                .responseFormat("json_object")
                .logRequests(true)
                .logResponses(true)
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
