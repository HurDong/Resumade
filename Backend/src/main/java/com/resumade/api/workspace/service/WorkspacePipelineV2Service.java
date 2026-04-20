package com.resumade.api.workspace.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.experience.domain.Experience;
import com.resumade.api.experience.domain.ExperienceRepository;
import com.resumade.api.experience.service.ExperienceVectorRetrievalService;
import com.resumade.api.infra.sse.Utf8SseSupport;
import com.resumade.api.workspace.domain.QuestionSnapshotRepository;
import com.resumade.api.workspace.domain.SnapshotType;
import com.resumade.api.workspace.domain.WorkspaceQuestion;
import com.resumade.api.workspace.domain.WorkspaceQuestionRepository;
import com.resumade.api.workspace.dto.DraftAnalysisResult;
import com.resumade.api.workspace.dto.DraftQualityResult;
import com.resumade.api.workspace.prompt.DraftParams;
import com.resumade.api.workspace.prompt.PromptFactory;
import com.resumade.api.workspace.prompt.QuestionCategory;
import com.resumade.api.workspace.prompt.QuestionProfile;
import dev.langchain4j.data.message.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * v2 자기소개서 생성 파이프라인 오케스트레이터.
 *
 * <h2>단계 요약</h2>
 * <ol>
 *   <li>QuestionAnalysisService — 문항 심층 분석 → QuestionProfile</li>
 *   <li>RAG — companyContext + experienceContext 구축</li>
 *   <li>Draft 생성 — PromptFactory.buildMessagesV2() + StrategyDraftGeneratorService</li>
 *   <li>2-Tier 품질 검수 — DraftQualityCheckService (최대 2회 retry)</li>
 *   <li>Wash — KO → EN → KO 번역 세탁</li>
 *   <li>Patch Analysis — 오역 감지 + 하이라이팅</li>
 * </ol>
 *
 * <p>기존 WorkspaceService.processHumanPatch()의 복잡한 로직(길이 확장 루프 12회,
 * 제목 하드코딩 검증 등)을 제거하고 명확한 2-Tier retry로 대체합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspacePipelineV2Service {

    private static final String STAGE_ANALYSIS = "ANALYSIS";
    private static final String STAGE_RAG      = "RAG";
    private static final String STAGE_DRAFT    = "DRAFT";
    private static final String STAGE_WASH     = "WASH";
    private static final String STAGE_PATCH    = "PATCH";
    private static final String STAGE_DONE     = "DONE";

    private static final int    MAX_RETRIES         = 2;
    private static final double TARGET_MIN_RATIO    = 0.80;
    private static final double TARGET_MAX_RATIO    = 1.00;
    private static final long   HEARTBEAT_INTERVAL  = 8L;

    private final WorkspaceQuestionRepository      questionRepository;
    private final ExperienceRepository             experienceRepository;
    private final ExperienceVectorRetrievalService experienceVectorRetrievalService;
    private final QuestionAnalysisService          questionAnalysisService;
    private final PromptFactory                    promptFactory;
    private final StrategyDraftGeneratorService    strategyDraftGeneratorService;
    private final DraftQualityCheckService         draftQualityCheckService;
    private final TranslationService               translationService;
    private final WorkspacePatchAiService          workspacePatchAiService;
    private final QuestionSnapshotService          questionSnapshotService;
    private final WorkspaceTaskCache               workspaceTaskCache;
    private final ObjectMapper                     objectMapper;

    // -------------------------------------------------------------------------
    // 진입점
    // -------------------------------------------------------------------------

    @Transactional
    public void processV2(Long questionId, boolean useDirective, Integer targetChars,
                          List<Long> storyIds, SseEmitter emitter) {
        HeartbeatHandle heartbeat = startHeartbeat(emitter);
        workspaceTaskCache.setRunning(questionId);
        try {
            run(questionId, useDirective, targetChars, storyIds, emitter);
        } catch (SseConnectionClosedException e) {
            log.info("V2 stream closed by client questionId={}", questionId);
        } catch (Exception e) {
            log.error("V2 pipeline failed questionId={}", questionId, e);
            workspaceTaskCache.setError(questionId, resolveErrorMessage(e));
            try {
                sendSse(emitter, "error", resolveErrorMessage(e));
            } catch (SseConnectionClosedException ignored) {
                log.info("V2 stream already closed while reporting error");
            }
            completeQuietly(emitter);
        } finally {
            heartbeat.stop();
        }
    }

    // -------------------------------------------------------------------------
    // 파이프라인 본체
    // -------------------------------------------------------------------------

    private void run(Long questionId, boolean useDirective, Integer targetChars,
                     List<Long> storyIds, SseEmitter emitter) throws Exception {

        WorkspaceQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found: " + questionId));

        String company       = question.getApplication().getCompanyName();
        String position      = question.getApplication().getPosition();
        String questionTitle = question.getTitle();
        int    maxLength     = question.getMaxLength();

        sendComment(emitter, "flush buffer");

        // ── STAGE 1: Question Analysis ─────────────────────────────────────
        sendProgress(emitter, STAGE_ANALYSIS, "문항의 의도와 요구 구조를 분석하고 있습니다. 🧠");
        QuestionProfile profile = questionAnalysisService.analyze(questionTitle);
        log.info("V2 analysis: category={} compound={} elements={} questionId={}",
                profile.primaryCategory(), profile.isCompound(),
                profile.requiredElements().size(), questionId);
        sendProgress(emitter, STAGE_ANALYSIS,
                String.format("문항 분석 완료 (%s%s). 맞춤 전략을 적용합니다. 🎯",
                        profile.primaryCategory().getDisplayName(),
                        profile.isCompound() ? " · 복합 문항" : ""));

        // ── STAGE 2: RAG ────────────────────────────────────────────────────
        sendProgress(emitter, STAGE_RAG, "기업 데이터와 경험 볼트에서 관련 자료를 찾고 있습니다. 🧭");
        String companyContext    = buildCompanyContext(question);
        String experienceContext = buildExperienceContext(question, questionId, profile, storyIds);
        String othersContext     = buildOthersContext(question, questionId);

        int minTarget     = (int) (maxLength * TARGET_MIN_RATIO);
        int preferredMax  = (int) (maxLength * TARGET_MAX_RATIO);

        String directive = buildDirective(question, useDirective, maxLength, targetChars);
        sendProgress(emitter, STAGE_RAG, "문항에 맞는 핵심 소재를 선별했습니다. 🧩");

        // ── STAGE 3: Draft Generation ────────────────────────────────────────
        sendProgress(emitter, STAGE_DRAFT, "선별한 경험 데이터를 바탕으로 초안을 생성 중입니다. ✍️");
        DraftParams params = DraftParams.builder()
                .company(company).position(position).questionTitle(questionTitle)
                .companyContext(companyContext).experienceContext(experienceContext)
                .othersContext(othersContext).directive(directive)
                .maxLength(maxLength).minTarget(minTarget).maxTarget(preferredMax)
                .build();

        String draft = generateWithRetry(profile, params, minTarget, preferredMax, emitter);

        question = questionRepository.findById(questionId).orElseThrow();
        question.setContent(draft);
        questionSnapshotService.saveSnapshot(questionId, SnapshotType.DRAFT_GENERATED, draft);
        questionRepository.save(question);
        sendSse(emitter, "draft_intermediate", draft);

        // ── STAGE 4: Wash ────────────────────────────────────────────────────
        sendProgress(emitter, STAGE_WASH, "초안 고도화를 위해 중간 번역 과정을 거치고 있습니다. 🌐");
        String translatedEn = translationService.translateToEnglish(draft);

        sendProgress(emitter, STAGE_WASH, "한국어로 다시 번역하며 표현을 더 자연스럽게 다듬고 있습니다. 🫧");
        String washedKr = prepareWashed(translationService.translateToKorean(translatedEn));

        question = questionRepository.findById(questionId).orElseThrow();
        question.setWashedKr(washedKr);
        questionRepository.save(question);
        questionSnapshotService.saveSnapshot(questionId, SnapshotType.WASHED, washedKr);
        sendSse(emitter, "washed_intermediate", washedKr);

        // ── STAGE 5: Patch Analysis ───────────────────────────────────────
        sendProgress(emitter, STAGE_PATCH, "원본과 번역본을 비교하며 오역을 검수하고 있습니다. 🔎");
        finalizePatch(emitter, questionId, draft, washedKr, maxLength, minTarget, preferredMax);
    }

    // -------------------------------------------------------------------------
    // Draft 생성 + 2-Tier Retry
    // -------------------------------------------------------------------------

    private String generateWithRetry(QuestionProfile profile, DraftParams params,
                                     int minTarget, int preferredMax, SseEmitter emitter) {
        List<ChatMessage> messages = promptFactory.buildMessagesV2(profile, params);
        WorkspaceDraftAiService.DraftResponse resp = strategyDraftGeneratorService.generate(messages);
        String draft = assembleDraft(resp);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            DraftQualityResult quality = draftQualityCheckService.check(draft, profile, minTarget);
            if (quality.passed()) {
                log.info("V2 draft passed quality check attempt={}", attempt == 1 ? 0 : attempt - 1);
                return draft;
            }

            log.info("V2 retry attempt={} lengthOk={} elementsOk={}", attempt,
                    quality.lengthOk(), quality.elementsOk());
            sendProgress(emitter, STAGE_DRAFT,
                    String.format("초안 품질을 개선하고 있습니다 (%d/%d). 🔄", attempt, MAX_RETRIES));

            List<ChatMessage> refineMessages = promptFactory.buildRefineMessagesV2(
                    profile, params, draft, quality.retryDirective());
            resp  = strategyDraftGeneratorService.generate(refineMessages);
            draft = assembleDraft(resp);
        }

        // 마지막 검수 통과 여부 무관하게 최선의 결과 반환
        return draft;
    }

    private String assembleDraft(WorkspaceDraftAiService.DraftResponse resp) {
        if (resp == null) return "";
        String title = resp.title != null ? resp.title.trim() : "";
        String text  = resp.text  != null ? resp.text.trim()  : "";
        if (title.isBlank()) return text;
        return title + "\n\n" + text;
    }

    // -------------------------------------------------------------------------
    // Patch Analysis
    // -------------------------------------------------------------------------

    private void finalizePatch(SseEmitter emitter, Long questionId, String originalDraft,
                                String washedKr, int maxLength, int minTarget, int preferredMax) throws Exception {
        int findingTarget = Math.max(1, washedKr.length() / 300);
        DraftAnalysisResult analysis;
        try {
            analysis = workspacePatchAiService.analyzePatch(
                    originalDraft, washedKr,
                    maxLength, minTarget, findingTarget, "");
        } catch (Exception e) {
            log.warn("V2 patch analysis failed, using washed draft as-is. reason={}", e.getMessage());
            analysis = DraftAnalysisResult.builder()
                    .mistranslations(List.of())
                    .aiReviewReport(DraftAnalysisResult.AiReviewReport.builder()
                            .summary("분석 실패 — 번역 결과를 그대로 사용합니다.").build())
                    .humanPatchedText(washedKr)
                    .build();
        }

        WorkspaceQuestion question = questionRepository.findById(questionId).orElseThrow();
        question.setMistranslations(objectMapper.writeValueAsString(analysis.getMistranslations()));
        question.setAiReview(objectMapper.writeValueAsString(analysis.getAiReviewReport()));
        questionRepository.save(question);
        questionSnapshotService.saveSnapshot(questionId, SnapshotType.WASHED,
                washedKr);

        String responseDraft = (analysis.getHumanPatchedText() != null && !analysis.getHumanPatchedText().isBlank())
                ? analysis.getHumanPatchedText()
                : washedKr;

        Map<String, Object> result = new HashMap<>();
        result.put("originalDraft", originalDraft);
        result.put("washedDraft",   washedKr);
        result.put("responseDraft", responseDraft);
        result.put("mistranslations", analysis.getMistranslations());
        result.put("aiReviewReport",  analysis.getAiReviewReport());
        result.put("minTarget",  minTarget);
        result.put("maxLength",  maxLength);

        workspaceTaskCache.setComplete(questionId, result);
        sendStage(emitter, STAGE_DONE);
        sendSse(emitter, "complete", result);
    }

    // -------------------------------------------------------------------------
    // Context Builders
    // -------------------------------------------------------------------------

    private String buildCompanyContext(WorkspaceQuestion question) {
        var app = question.getApplication();
        List<String> sections = new ArrayList<>();

        if (app.getCompanyResearch() != null && !app.getCompanyResearch().isBlank()) {
            sections.add("[Company Research]\n" + app.getCompanyResearch());
        }
        if (app.getAiInsight() != null && !app.getAiInsight().isBlank()) {
            sections.add("[JD Insight]\n" + app.getAiInsight());
        }
        if (app.getRawJd() != null && !app.getRawJd().isBlank()) {
            sections.add("[Raw JD]\n" + app.getRawJd());
        }
        return sections.isEmpty()
                ? "No company-specific context available."
                : String.join("\n---\n", sections);
    }

    private String buildExperienceContext(WorkspaceQuestion question, Long questionId,
                                           QuestionProfile profile, List<Long> storyIds) {
        if (storyIds != null && !storyIds.isEmpty()) {
            // 사용자가 직접 선택한 스토리
            return experienceRepository.findAllById(storyIds).stream()
                    .map(Experience::getRawContent)
                    .collect(Collectors.joining("\n---\n"));
        }

        // RAG: profile.ragKeywords를 supporting queries로 사용
        List<String> supportingQueries = new ArrayList<>(profile.ragKeywords());

        var results = experienceVectorRetrievalService.search(
                question.getTitle(), 4, Set.of(), supportingQueries,
                profile.primaryCategory());

        if (!results.isEmpty()) {
            return results.stream()
                    .map(item -> {
                        String header = (item.getFacetTitle() != null && !item.getFacetTitle().isBlank())
                                ? String.format("[%s | Facet: %s]", item.getExperienceTitle(), item.getFacetTitle())
                                : String.format("[%s]", item.getExperienceTitle());
                        return header + "\n" + item.getRelevantPart();
                    })
                    .collect(Collectors.joining("\n---\n"));
        }

        // RAG 결과 없으면 전체 경험 로드
        return experienceRepository.findAll().stream()
                .map(Experience::getRawContent)
                .collect(Collectors.joining("\n---\n"));
    }

    private String buildOthersContext(WorkspaceQuestion currentQuestion, Long questionId) {
        return currentQuestion.getApplication().getQuestions().stream()
                .filter(q -> !q.getId().equals(questionId))
                .filter(q -> q.getContent() != null && !q.getContent().isBlank())
                .map(q -> String.format("[%s]\n%s",
                        q.getTitle(),
                        q.getContent().length() > 200
                                ? q.getContent().substring(0, 200) + "..."
                                : q.getContent()))
                .collect(Collectors.joining("\n---\n"));
    }

    private String buildDirective(WorkspaceQuestion question, boolean useDirective,
                                   int maxLength, Integer targetChars) {
        if (!useDirective) return "No extra user directive.";
        List<String> parts = new ArrayList<>();
        if (question.getBatchStrategyDirective() != null && !question.getBatchStrategyDirective().isBlank()) {
            parts.add(question.getBatchStrategyDirective().trim());
        }
        if (question.getUserDirective() != null && !question.getUserDirective().isBlank()) {
            parts.add(question.getUserDirective().trim());
        }
        if (targetChars != null) {
            parts.add(String.format("목표 글자 수: %d자", targetChars));
        }
        return parts.isEmpty() ? "No extra user directive." : String.join("\n", parts);
    }

    // -------------------------------------------------------------------------
    // Wash 후처리
    // -------------------------------------------------------------------------

    private String prepareWashed(String text) {
        if (text == null) return "";
        return text.trim()
                .replaceAll("\\r\\n", "\n")
                .replaceAll("\\r", "\n")
                .replaceAll("\n{3,}", "\n\n");
    }

    // -------------------------------------------------------------------------
    // SSE 헬퍼
    // -------------------------------------------------------------------------

    private void sendProgress(SseEmitter emitter, String stage, String message) {
        Map<String, String> payload = new HashMap<>();
        payload.put("stage", stage);
        payload.put("message", message);
        sendSse(emitter, "progress", payload);
    }

    private void sendStage(SseEmitter emitter, String stage) {
        sendSse(emitter, "stage", Map.of("stage", stage));
    }

    private void sendSse(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(Utf8SseSupport.jsonEvent(name, data));
        } catch (IOException e) {
            if (isClientDisconnect(e)) throw new SseConnectionClosedException(e);
            log.warn("SSE send failed name={} reason={}", name, e.getMessage());
        }
    }

    private void sendComment(SseEmitter emitter, String comment) {
        try {
            emitter.send(SseEmitter.event().comment(comment));
        } catch (IOException e) {
            if (isClientDisconnect(e)) throw new SseConnectionClosedException(e);
        }
    }

    private boolean isClientDisconnect(Exception e) {
        String msg = e.getMessage();
        return msg != null && (msg.contains("Broken pipe") || msg.contains("Connection reset")
                || msg.contains("closed") || msg.contains("aborted"));
    }

    private void completeQuietly(SseEmitter emitter) {
        try { emitter.complete(); } catch (Exception ignored) {}
    }

    private HeartbeatHandle startHeartbeat(SseEmitter emitter) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try { emitter.send(SseEmitter.event().comment("heartbeat")); }
            catch (Exception ignored) {}
        }, HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL, TimeUnit.SECONDS);
        return new HeartbeatHandle(scheduler, future);
    }

    private String resolveErrorMessage(Exception e) {
        if (e instanceof IllegalArgumentException) return e.getMessage();
        return "서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";
    }

    private record HeartbeatHandle(ScheduledExecutorService scheduler, ScheduledFuture<?> future) {
        void stop() {
            future.cancel(false);
            scheduler.shutdownNow();
        }
    }

    private static class SseConnectionClosedException extends RuntimeException {
        SseConnectionClosedException(Throwable cause) { super(cause); }
    }
}
