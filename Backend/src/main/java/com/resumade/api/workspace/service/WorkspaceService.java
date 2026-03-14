package com.resumade.api.workspace.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.experience.domain.Experience;
import com.resumade.api.experience.domain.ExperienceRepository;
import com.resumade.api.workspace.domain.WorkspaceQuestion;
import com.resumade.api.workspace.domain.WorkspaceQuestionRepository;
import com.resumade.api.workspace.dto.DraftAnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final ExperienceRepository experienceRepository;
    private final WorkspaceAiService workspaceAiService;
    private final TranslationService translationService;
    private final ObjectMapper objectMapper;
    private final WorkspaceQuestionRepository questionRepository;

    public void processHumanPatch(Long questionId, SseEmitter emitter) {
        CompletableFuture.runAsync(() -> {
            try {
                WorkspaceQuestion question = questionRepository.findById(questionId)
                        .orElseThrow(() -> new RuntimeException("Question not found: " + questionId));
                
                String company = question.getApplication().getCompanyName();
                String position = question.getApplication().getPosition();
                String questionTitle = question.getTitle();

                // Step 1: RAG Context Search
                sendSse(emitter, "progress", "RAG: 관련 경험 데이터를 추출 중입니다...");
                List<Experience> experiences = experienceRepository.findAll();
                String context = experiences.stream()
                        .map(Experience::getRawContent)
                        .collect(Collectors.joining("\n---\n"));

                // Step 2: Generate Draft
                sendSse(emitter, "progress", "DRAFT: AI가 자소서 초안을 작성 중입니다...");
                WorkspaceAiService.DraftResponse draftResponse = workspaceAiService.generateDraft(company, position, questionTitle, context);
                String draft = draftResponse.text;
                
                question.setContent(draft);
                questionRepository.save(question);
                sendSse(emitter, "draft_intermediate", draft);

                // Step 3: Translate to English (DeepL)
                sendSse(emitter, "progress", "TRANSLATE_EN: 영어로 번역하여 AI 탐지기를 세탁 중입니다...");
                String translatedEn = translationService.translateToEnglish(draft);

                // Step 4: Translate back to Korean (Papago/DeepL)
                sendSse(emitter, "progress", "TRANSLATE_KR: 다시 한국어로 번역하여 자연스럽게 다듬는 중입니다...");
                String washedKr = translationService.translateToKorean(translatedEn);
                
                question.setWashedKr(washedKr);
                questionRepository.save(question);
                sendSse(emitter, "washed_intermediate", washedKr);

                // Step 5: Human Patch & Analysis
                sendSse(emitter, "progress", "PATCH: 최종 휴먼 패치 및 오역 검토를 진행 중입니다...");
                DraftAnalysisResult analysis = workspaceAiService.analyzePatch(draft, washedKr);
                
                question.setMistranslations(objectMapper.writeValueAsString(analysis.getMistranslations()));
                question.setAiReview(objectMapper.writeValueAsString(analysis.getAiReviewReport()));
                questionRepository.save(question);

                Map<String, Object> result = new HashMap<>();
                result.put("draft", washedKr);
                result.put("mistranslations", analysis.getMistranslations());
                result.put("aiReviewReport", analysis.getAiReviewReport());

                sendSse(emitter, "complete", result);
                emitter.complete();

            } catch (Exception e) {
                log.error("Human Patch process failed", e);
                sendSse(emitter, "error", "처리 중 오류가 발생했습니다: " + e.getMessage());
                emitter.completeWithError(e);
            }
        });
    }

    private void sendSse(SseEmitter emitter, String name, Object data) {
        try {
            Object sseData = data;
            if (!(data instanceof String)) {
                sseData = objectMapper.writeValueAsString(data);
            }
            emitter.send(SseEmitter.event().name(name).data(sseData));
        } catch (IOException e) {
            log.warn("Failed to send SSE event: {}", name);
        }
    }
}
