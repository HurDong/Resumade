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
import org.springframework.transaction.annotation.Transactional;
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

    public List<com.resumade.api.workspace.dto.ExperienceContextResponse.ContextItem> getMatchedExperiences(Long questionId, String customQuery) {
        WorkspaceQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found: " + questionId));
        
        List<Experience> allExperiences = experienceRepository.findAll();
        String query = (customQuery != null && !customQuery.isBlank()) ? customQuery : question.getTitle();

        // Simple relevance matching logic for now (can be improved with Elasticsearch later)
        return allExperiences.stream()
                .map(exp -> {
                    int score = calculateRelevance(query, exp.getRawContent());
                    return com.resumade.api.workspace.dto.ExperienceContextResponse.ContextItem.builder()
                            .id("exp-" + exp.getId())
                            .experienceTitle(exp.getTitle())
                            .relevantPart(exp.getDescription())
                            .relevanceScore(score)
                            .build();
                })
                .sorted((a, b) -> Integer.compare(b.getRelevanceScore(), a.getRelevanceScore()))
                .limit(3)
                .collect(Collectors.toList());
    }

    private int calculateRelevance(String query, String content) {
        if (query == null || content == null) return 0;
        String[] words = query.split("\\s+");
        long matchCount = java.util.Arrays.stream(words)
                .filter(word -> word.length() > 1 && content.contains(word))
                .count();
        
        // Base matching score 50 + bonus for hits (max 99)
        int score = 50 + (int)(matchCount * 10);
        return Math.min(score, 99);
    }

    @Transactional(readOnly = true)
    public void processRefinement(Long questionId, String directive, SseEmitter emitter) {
        try {
            WorkspaceQuestion initialQuestion = questionRepository.findById(questionId)
                    .orElseThrow(() -> new RuntimeException("Question not found: " + questionId));

            String company = initialQuestion.getApplication().getCompanyName();
            String position = initialQuestion.getApplication().getPosition();
            
            // For refinement, we use the current washed text if available, otherwise fallback to draft
            String currentInput = initialQuestion.getWashedKr() != null ? initialQuestion.getWashedKr() : initialQuestion.getContent();

            // Step 1: Collect Context (Experiences)
            sendSse(emitter, "progress", "RAG: 관련 경험 데이터를 재추출 중입니다...");
            List<Experience> experiences = experienceRepository.findAll();
            String context = experiences.stream()
                    .map(Experience::getRawContent)
                    .collect(Collectors.joining("\n---\n"));
            paceProcessing();

            // Step 2: Refine Draft with new Directive
            sendSse(emitter, "progress", "DRAFT: 지시사항을 반영하여 초안을 정교하게 수정 중입니다...");
            WorkspaceAiService.DraftResponse refineResponse = workspaceAiService.refineDraft(
                    company,
                    position,
                    currentInput,
                    initialQuestion.getMaxLength(),
                    context,
                    directive
            );
            String refinedDraft = refineResponse.text;

            // Post-process
            refinedDraft = refinedDraft.replaceAll("\\*\\*\\[", "[")
                                     .replaceAll("\\]\\*\\*", "]");
            if (refinedDraft.contains("]") && !refinedDraft.contains("]\n\n")) {
                refinedDraft = refinedDraft.replaceFirst("\\]\n", "]\n\n");
            }

            // Save refined draft as the new base content
            WorkspaceQuestion question = questionRepository.findById(questionId).orElseThrow();
            question.setContent(refinedDraft);
            question.setUserDirective(directive); // Update Directive for history
            questionRepository.save(question);
            sendSse(emitter, "draft_intermediate", refinedDraft);

            // Step 3: Translate to English (DeepL)
            paceProcessing();
            sendSse(emitter, "progress", "TRANSLATE_EN: 다시 영어로 번역하여 AI 탐지기를 세탁 중입니다...");
            String translatedEn = translationService.translateToEnglish(refinedDraft);

            // Step 4: Translate back to Korean (Papago/DeepL)
            paceProcessing();
            sendSse(emitter, "progress", "TRANSLATE_KR: 자연스러운 한국어로 새롭게 다듬는 중입니다...");
            String washedKr = translationService.translateToKorean(translatedEn);

            // Post-process washed text
            washedKr = washedKr.replaceAll("\\*\\*\\[", "[")
                               .replaceAll("\\]\\*\\*", "]");
            if (washedKr.contains("]") && !washedKr.contains("]\n\n")) {
                washedKr = washedKr.replaceFirst("\\]\n", "]\n\n");
            }

            question = questionRepository.findById(questionId).orElseThrow();
            question.setWashedKr(washedKr);
            questionRepository.save(question);
            sendSse(emitter, "washed_intermediate", washedKr);

            // Step 5: Human Patch & Analysis
            paceProcessing();
            sendSse(emitter, "progress", "PATCH: 최종 휴먼 패치 및 오역 검토를 진행 중입니다...");
            DraftAnalysisResult analysis = workspaceAiService.analyzePatch(refinedDraft, washedKr, initialQuestion.getMaxLength());
            
            paceProcessing();

            // Post-process analysis
            analysis.getMistranslations().forEach(m -> {
                if (m.getSuggestion() != null) {
                    m.setSuggestion(m.getSuggestion().replaceAll("\\*\\*\\[", "[").replaceAll("\\]\\*\\*", "]"));
                }
            });

            question = questionRepository.findById(questionId).orElseThrow();
            question.setMistranslations(objectMapper.writeValueAsString(analysis.getMistranslations()));
            question.setAiReview(objectMapper.writeValueAsString(analysis.getAiReviewReport()));
            
            // AI가 글자수를 수동으로 맞춘 humanPatchedText를 데이터로는 내려주되, DB의 washedKr은 원본 세탁본(하이라이팅용)을 유지합니다.
            String responseDraft = (analysis.getHumanPatchedText() != null && !analysis.getHumanPatchedText().isBlank())
                    ? analysis.getHumanPatchedText()
                    : washedKr;

            Map<String, Object> result = new HashMap<>();
            result.put("draft", washedKr); // Send original washed for highlighting matching
            result.put("humanPatched", responseDraft); // For display/final use
            result.put("mistranslations", analysis.getMistranslations());
            result.put("aiReviewReport", analysis.getAiReviewReport());

            sendSse(emitter, "complete", result);
            emitter.complete();

        } catch (Exception e) {
            log.error("Refinement process failed", e);
            sendSse(emitter, "error", "수정 처리 중 오류가 발생했습니다: " + e.getMessage());
            try {
                emitter.complete();
            } catch (Exception ex) {
                // Ignore
            }
        }
    }

    @Transactional(readOnly = true)
    public void processHumanPatch(Long questionId, SseEmitter emitter) {
        try {
            WorkspaceQuestion initialQuestion = questionRepository.findById(questionId)
                    .orElseThrow(() -> new RuntimeException("Question not found: " + questionId));
            
            String company = initialQuestion.getApplication().getCompanyName();
            String position = initialQuestion.getApplication().getPosition();
            String questionTitle = initialQuestion.getTitle();
            
            // Collect other questions' info for non-overlap
            String others = initialQuestion.getApplication().getQuestions().stream()
                    .filter(q -> !q.getId().equals(questionId))
                    .map(q -> String.format("[문항: %s | 내용: %s]", q.getTitle(), q.getContent() != null ? q.getContent() : "아직 작성 전"))
                    .collect(Collectors.joining("\n"));

            // Step 1: RAG Context Search
            sendSse(emitter, "progress", "RAG: 관련 경험 데이터를 추출 중입니다...");
            List<Experience> experiences = experienceRepository.findAll();
            String context = experiences.stream()
                    .map(Experience::getRawContent)
                    .collect(Collectors.joining("\n---\n"));
            paceProcessing();

            // Step 2: Generate Draft
            sendSse(emitter, "progress", "DRAFT: AI가 전체 자소서와의 조화를 고려하여 초안을 작성 중입니다...");
            
            WorkspaceAiService.DraftResponse draftResponse = workspaceAiService.generateDraft(
                    company, 
                    position, 
                    questionTitle, 
                    initialQuestion.getMaxLength(), 
                    context, 
                    others,
                    initialQuestion.getUserDirective() != null ? initialQuestion.getUserDirective() : "없음"
            );
            String draft = draftResponse.text;
            
            // Post-process to ensure formatting: Strip bold from titles and ensure newline
            draft = draft.replaceAll("\\*\\*\\[", "[")
                         .replaceAll("\\]\\*\\*", "]");
            if (draft.contains("]") && !draft.contains("]\n\n")) {
                draft = draft.replaceFirst("\\]\n", "]\n\n");
            }
            
            // Re-fetch question for update
            WorkspaceQuestion question = questionRepository.findById(questionId).orElseThrow();
            question.setContent(draft);
            questionRepository.save(question);
            sendSse(emitter, "draft_intermediate", draft);

            // Step 3: Translate to English (DeepL)
            paceProcessing();
            sendSse(emitter, "progress", "TRANSLATE_EN: 영어로 번역하여 AI 탐지기를 세탁 중입니다...");
            String translatedEn = translationService.translateToEnglish(draft);

            // Step 4: Translate back to Korean (Papago/DeepL)
            paceProcessing();
            sendSse(emitter, "progress", "TRANSLATE_KR: 다시 한국어로 번역하여 자연스럽게 다듬는 중입니다...");
            String washedKr = translationService.translateToKorean(translatedEn);
            
            // Post-process washed text similarly
            washedKr = washedKr.replaceAll("\\*\\*\\[", "[")
                               .replaceAll("\\]\\*\\*", "]");
            if (washedKr.contains("]") && !washedKr.contains("]\n\n")) {
                washedKr = washedKr.replaceFirst("\\]\n", "]\n\n");
            }

            question = questionRepository.findById(questionId).orElseThrow();
            question.setWashedKr(washedKr);
            questionRepository.save(question);
            sendSse(emitter, "washed_intermediate", washedKr);

            // Step 5: Human Patch & Analysis
            paceProcessing();
            sendSse(emitter, "progress", "PATCH: 최종 휴먼 패치 및 오역 검토를 진행 중입니다...");
            DraftAnalysisResult analysis = workspaceAiService.analyzePatch(draft, washedKr, initialQuestion.getMaxLength());
            
            paceProcessing(); // Final breath before completion
            
            // Ensure suggestions also don't have bolding if they were titles
            analysis.getMistranslations().forEach(m -> {
                if (m.getSuggestion() != null) {
                    m.setSuggestion(m.getSuggestion().replaceAll("\\*\\*\\[", "[").replaceAll("\\]\\*\\*", "]"));
                }
            });

            question = questionRepository.findById(questionId).orElseThrow();
            question.setMistranslations(objectMapper.writeValueAsString(analysis.getMistranslations()));
            question.setAiReview(objectMapper.writeValueAsString(analysis.getAiReviewReport()));
            
            // AI가 글자수를 수동으로 맞춘 humanPatchedText를 데이터로는 내려주되, DB의 washedKr은 원본 세탁본(하이라이팅용)을 유지합니다.
            String responseDraft = (analysis.getHumanPatchedText() != null && !analysis.getHumanPatchedText().isBlank())
                    ? analysis.getHumanPatchedText()
                    : washedKr;

            Map<String, Object> result = new HashMap<>();
            result.put("draft", washedKr); // Send original washed for highlighting matching
            result.put("humanPatched", responseDraft); // For display/final use
            result.put("mistranslations", analysis.getMistranslations());
            result.put("aiReviewReport", analysis.getAiReviewReport());

            sendSse(emitter, "complete", result);
            emitter.complete();

        } catch (Exception e) {
            log.error("Human Patch process failed", e);
            sendSse(emitter, "error", "처리 중 오류가 발생했습니다: " + e.getMessage());
            try {
                emitter.complete();
            } catch (Exception ex) {
                // Ignore if already completed
            }
        }
    }

    private void paceProcessing() {
        try {
            Thread.sleep(700);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
