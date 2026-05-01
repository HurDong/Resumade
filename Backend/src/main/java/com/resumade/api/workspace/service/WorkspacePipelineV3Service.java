package com.resumade.api.workspace.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.experience.domain.PersonalStory;
import com.resumade.api.experience.domain.PersonalStoryRepository;
import com.resumade.api.experience.service.ExperienceVectorRetrievalService;
import com.resumade.api.infra.sse.Utf8SseSupport;
import com.resumade.api.workspace.domain.SnapshotType;
import com.resumade.api.workspace.domain.WorkspaceQuestion;
import com.resumade.api.workspace.domain.WorkspaceQuestionRepository;
import com.resumade.api.workspace.dto.DraftAuthenticityReport;
import com.resumade.api.workspace.prompt.DraftParams;
import com.resumade.api.workspace.prompt.PromptFactory;
import com.resumade.api.workspace.prompt.QuestionCategory;
import com.resumade.api.workspace.prompt.QuestionDraftPlan;
import com.resumade.api.workspace.prompt.QuestionDraftPlanV3;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspacePipelineV3Service {

    private static final String STAGE_ANALYSIS = "ANALYSIS";
    private static final String STAGE_RAG = "RAG";
    private static final String STAGE_DRAFT = "DRAFT";
    private static final String STAGE_WASH = "WASH";
    private static final String STAGE_DONE = "DONE";

    private static final double TARGET_MIN_RATIO = 0.80;
    private static final double REQUESTED_TARGET_MIN_RATIO = 0.90;
    private static final double DEFAULT_DESIRED_TARGET_RATIO = 0.90;
    private static final long HEARTBEAT_INTERVAL = 8L;

    private static final String NO_VERIFIED_EXPERIENCE_TITLE = "근거 경험 선택 필요";
    private static final String NO_VERIFIED_EXPERIENCE_MESSAGE =
            "이 문항에 연결할 검증된 경험이 아직 선택되거나 검색되지 않았습니다. 경험 보관소에서 관련 경험을 선택하거나 새 경험을 추가한 뒤 다시 생성해 주세요.";
    private static final String NO_VERIFIED_EXPERIENCE_CONTEXT = """
            NO_VERIFIED_EXPERIENCE_CONTEXT
            No RAG match or selected personal story was available for this question.
            Do not invent applicant incidents, metrics, tools, certifications, or roles.
            Return a short JSON answer asking the user to select or add a verified experience before drafting.
            """;

    private final WorkspaceQuestionRepository questionRepository;
    private final PersonalStoryRepository personalStoryRepository;
    private final ExperienceVectorRetrievalService experienceVectorRetrievalService;
    private final QuestionDraftPlannerService questionDraftPlannerService;
    private final PromptFactory promptFactory;
    private final StrategyDraftGeneratorService strategyDraftGeneratorService;
    private final DraftCriticRewriteService draftCriticRewriteService;
    private final DraftAuthenticityReviewService draftAuthenticityReviewService;
    private final TranslationService translationService;
    private final QuestionSnapshotService questionSnapshotService;
    private final WorkspaceTaskCache workspaceTaskCache;
    private final ObjectMapper objectMapper;

    @Transactional
    public void processV3(Long questionId, boolean useDirective, Integer targetChars,
                          List<Long> storyIds, SseEmitter emitter) {
        HeartbeatHandle heartbeat = startHeartbeat(emitter);
        workspaceTaskCache.setRunning(questionId);
        try {
            run(questionId, useDirective, targetChars, storyIds, emitter);
        } catch (SseConnectionClosedException e) {
            log.info("[v3 stream closed] questionId={}", questionId);
        } catch (Exception e) {
            log.error("[v3 pipeline failed] questionId={}", questionId, e);
            workspaceTaskCache.setError(questionId, resolveErrorMessage(e));
            try {
                sendSse(emitter, "error", Map.of("message", resolveErrorMessage(e)));
            } catch (SseConnectionClosedException ignored) {
                log.info("[v3 stream closed while sending error] questionId={}", questionId);
            }
            completeQuietly(emitter);
        } finally {
            heartbeat.stop();
        }
    }

    private void run(Long questionId, boolean useDirective, Integer targetChars,
                     List<Long> storyIds, SseEmitter emitter) throws Exception {
        WorkspaceQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found: " + questionId));

        String company = question.getApplication().getCompanyName();
        String position = question.getApplication().getPosition();
        String questionTitle = question.getTitle();
        int maxLength = question.getMaxLength();
        LengthPolicy lengthPolicy = resolveLengthPolicy(maxLength, targetChars);
        String directive = buildDirective(question, useDirective, targetChars);

        sendComment(emitter, "flush buffer");
        sendProgress(emitter, STAGE_ANALYSIS, "문항 의도와 v3 답변 설계안을 생성하고 있습니다.");
        QuestionDraftPlanV3 plan = questionDraftPlannerService.planV3(
                company,
                position,
                questionTitle,
                maxLength,
                lengthPolicy.minTarget(),
                lengthPolicy.desiredTarget(),
                directive
        );
        log.info("[v3 plan] questionId={} category={} compound={} target={}~{} hard={}",
                questionId, plan.primaryCategory(), plan.compound(),
                lengthPolicy.minTarget(), lengthPolicy.desiredTarget(), maxLength);

        sendProgress(emitter, STAGE_RAG, "v3 설계안에 맞는 검증 경험을 검색하고 있습니다.");
        String companyContext = buildCompanyContext(question, plan);
        String experienceContext = buildExperienceContext(questionId, plan, storyIds);
        String othersContext = buildOthersContext(question, questionId);

        if (isNoVerifiedExperienceContext(experienceContext)) {
            completeWithNoVerifiedExperience(emitter, questionId, maxLength,
                    lengthPolicy.minTarget(), lengthPolicy.desiredTarget());
            return;
        }

        DraftParams params = DraftParams.builder()
                .company(company)
                .position(position)
                .questionTitle(questionTitle)
                .companyContext(companyContext)
                .experienceContext(experienceContext)
                .othersContext(othersContext)
                .directive(directive)
                .draftPlanContext(toPlanJson(plan))
                .maxLength(maxLength)
                .minTarget(lengthPolicy.minTarget())
                .maxTarget(lengthPolicy.desiredTarget())
                .build();

        sendProgress(emitter, STAGE_DRAFT, "검증 가능한 경험과 v3 문체 규칙으로 초안을 작성하고 있습니다.");
        WorkspaceDraftAiService.DraftResponse generated = strategyDraftGeneratorService.generate(
                promptFactory.buildDraftPlanMessagesV3(plan, params));
        String draft = trimToHardLimit(assembleDraft(generated), maxLength);

        sendProgress(emitter, STAGE_DRAFT, "경험 밀도와 면접 설명 가능성을 검수하고 있습니다.");
        DraftAuthenticityReport qualityReport = draftAuthenticityReviewService.review(params, plan, draft);
        sendSse(emitter, "quality_report", qualityReport);

        if (qualityReport.requiresRewrite()) {
            sendProgress(emitter, STAGE_DRAFT, "v3 검수 결과를 반영해 문체와 근거를 보강하고 있습니다.");
            WorkspaceDraftAiService.DraftResponse rewritten = draftCriticRewriteService.rewriteForAuthenticity(
                    params, draft, qualityReport);
            String repaired = trimToHardLimit(assembleDraft(rewritten), maxLength);
            if (!repaired.isBlank()) {
                draft = repaired;
                qualityReport = draftAuthenticityReviewService.review(params, plan, draft);
                sendSse(emitter, "quality_report", qualityReport);
            }
        }

        saveDraft(questionId, draft);
        sendSse(emitter, "draft_intermediate", draft);

        sendProgress(emitter, STAGE_WASH, "번역 왕복 세탁 후 최종 문체와 길이를 확인하고 있습니다.");
        String washedKr;
        try {
            String translatedEn = translationService.translateToEnglish(draft);
            washedKr = prepareWashed(translationService.translateToKorean(translatedEn));
        } catch (Exception e) {
            log.warn("[v3 wash failed] questionId={} reason={}", questionId, e.getMessage());
            washedKr = draft;
        }

        DraftAuthenticityReport finalReport = draftAuthenticityReviewService.review(params, plan, washedKr);
        sendSse(emitter, "quality_report", finalReport);
        saveWashed(questionId, washedKr, finalReport);
        sendSse(emitter, "washed_intermediate", washedKr);

        int finalLength = countVisibleChars(washedKr);
        boolean lengthOk = isFinalLengthOk(finalLength, lengthPolicy);
        String warningMessage = lengthOk
                ? null
                : String.format("v3 세탁본이 %d자입니다. 목표 범위(%d-%d자)를 벗어났습니다.",
                finalLength, lengthPolicy.minTarget(), lengthPolicy.hardLimit());
        Map<String, Object> payload = buildCompletionPayload(
                washedKr,
                draft,
                finalReport,
                maxLength,
                lengthPolicy.minTarget(),
                lengthPolicy.desiredTarget(),
                finalLength,
                lengthOk,
                warningMessage
        );
        workspaceTaskCache.setComplete(questionId, payload);
        sendStage(emitter, STAGE_DONE);
        sendSse(emitter, "complete", payload);
        completeQuietly(emitter);
    }

    Map<String, Object> buildCompletionPayload(
            String washedDraft,
            String sourceDraft,
            DraftAuthenticityReport qualityReport,
            int maxLength,
            int minTarget,
            int preferredMax,
            int finalLength,
            boolean lengthOk,
            String warningMessage
    ) {
        Map<String, Object> aiReviewReport = buildAiReviewReport(qualityReport);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("draft", washedDraft);
        result.put("washedDraft", washedDraft);
        result.put("sourceDraft", sourceDraft);
        result.put("mistranslations", List.of());
        result.put("aiReviewReport", aiReviewReport);
        result.put("qualityReport", qualityReport);
        result.put("lengthOk", lengthOk);
        result.put("finalLength", finalLength);
        result.put("minTarget", minTarget);
        result.put("maxLength", maxLength);
        result.put("preferredMax", preferredMax);
        result.put("warningMessage", warningMessage);
        return result;
    }

    private Map<String, Object> buildAiReviewReport(DraftAuthenticityReport report) {
        Map<String, Object> aiReviewReport = new LinkedHashMap<>();
        aiReviewReport.put("summary", report == null ? "v3 품질 리포트를 생성하지 못했습니다." : report.summary());
        aiReviewReport.put("qualityReport", report);
        if (report != null) {
            aiReviewReport.put("overallScore", report.interviewDefensibilityScore());
            aiReviewReport.put("readability", Math.max(0, 100 - report.authenticityRiskScore()));
            aiReviewReport.put("technicalAccuracy", report.experienceDensityScore());
        }
        return aiReviewReport;
    }

    private void completeWithNoVerifiedExperience(SseEmitter emitter, Long questionId,
                                                  int maxLength, int minTarget, int preferredMax) {
        String draft = "[" + NO_VERIFIED_EXPERIENCE_TITLE + "]\n" + NO_VERIFIED_EXPERIENCE_MESSAGE;
        DraftAuthenticityReport report = DraftAuthenticityReport.builder()
                .experienceDensityScore(0)
                .authenticityRiskScore(0)
                .interviewDefensibilityScore(0)
                .factGaps(List.of(NO_VERIFIED_EXPERIENCE_MESSAGE))
                .verificationQuestions(List.of("이 문항에 연결할 실제 경험은 무엇인가요?", "성과나 역할을 증명할 자료가 있나요?"))
                .rewriteDirective("경험 보관소에서 관련 경험을 선택하거나 추가한 뒤 다시 생성하세요.")
                .summary(NO_VERIFIED_EXPERIENCE_MESSAGE)
                .build();
        Map<String, Object> result = buildCompletionPayload(
                draft, draft, report, maxLength, minTarget, preferredMax,
                countVisibleChars(draft), false, NO_VERIFIED_EXPERIENCE_MESSAGE);
        result.put("requiresExperienceSelection", true);
        workspaceTaskCache.setComplete(questionId, result);
        sendStage(emitter, STAGE_DONE);
        sendSse(emitter, "draft_intermediate", draft);
        sendSse(emitter, "quality_report", report);
        sendSse(emitter, "complete", result);
        completeQuietly(emitter);
    }

    private String buildCompanyContext(WorkspaceQuestion question, QuestionDraftPlanV3 plan) {
        var app = question.getApplication();
        List<String> sections = new ArrayList<>();
        sections.add("[Question Intent]\n"
                + "Category: " + plan.primaryCategory() + "\n"
                + "Dominant spine: " + plan.dominantSpine() + "\n"
                + "Verification focus: " + String.join(", ", plan.verificationFocus()));
        if (app.getCompanyResearch() != null && !app.getCompanyResearch().isBlank()) {
            sections.add("[Company Research]\n" + app.getCompanyResearch());
        }
        if (app.getAiInsight() != null && !app.getAiInsight().isBlank()) {
            sections.add("[JD Insight]\n" + app.getAiInsight());
        }
        if (app.getRawJd() != null && !app.getRawJd().isBlank()) {
            sections.add("[Raw JD]\n" + app.getRawJd());
        }
        return String.join("\n---\n", sections);
    }

    private String buildExperienceContext(Long questionId, QuestionDraftPlanV3 plan, List<Long> storyIds) {
        if (storyIds != null && !storyIds.isEmpty()) {
            return buildPersonalStoryContext(storyIds);
        }

        QuestionDraftPlan basePlan = plan.basePlan();
        if (plan.primaryCategory() == QuestionCategory.PERSONAL_GROWTH) {
            List<Long> allStoryIds = personalStoryRepository.findAllByOrderByIdAsc().stream()
                    .map(PersonalStory::getId)
                    .toList();
            if (!allStoryIds.isEmpty()) {
                return buildPersonalStoryContext(allStoryIds);
            }
        }

        boolean reflectivePlan = isReflectiveNarrativePlan(basePlan);
        var results = experienceVectorRetrievalService.search(
                basePlan.experienceNeeds(),
                reflectivePlan ? 4 : 6,
                Set.of(),
                plan.primaryCategory());

        if (!results.isEmpty()) {
            logRagMatches(questionId, plan.primaryCategory(), results);
            Map<String, List<com.resumade.api.workspace.dto.ExperienceContextResponse.ContextItem>> grouped = new LinkedHashMap<>();
            for (var item : results) {
                String key = item.getExperienceId() + ":" + item.getFacetId();
                grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
            }
            return grouped.values().stream()
                    .limit(reflectivePlan ? 2 : 4)
                    .map(items -> {
                        var first = items.get(0);
                        String header = first.getFacetTitle() != null && !first.getFacetTitle().isBlank()
                                ? String.format("[%s | Facet: %s]", first.getExperienceTitle(), first.getFacetTitle())
                                : String.format("[%s]", first.getExperienceTitle());
                        String body = items.stream()
                                .limit(reflectivePlan ? 3 : 5)
                                .map(item -> {
                                    String unitLabel = item.getUnitType() == null || item.getUnitType().isBlank()
                                            ? "UNIT"
                                            : item.getUnitType();
                                    return "- " + unitLabel + ": " + item.getRelevantPart();
                                })
                                .collect(Collectors.joining("\n"));
                        return header + "\n" + body;
                    })
                    .collect(Collectors.joining("\n---\n"));
        }

        return NO_VERIFIED_EXPERIENCE_CONTEXT;
    }

    private boolean isReflectiveNarrativePlan(QuestionDraftPlan plan) {
        if (plan == null) {
            return false;
        }
        String marker = String.join(" ",
                firstNonBlank(plan.questionIntent(), ""),
                firstNonBlank(plan.answerPosture(), ""),
                firstNonBlank(plan.evidencePolicy(), ""),
                firstNonBlank(plan.companyConnectionPolicy(), ""));
        return marker.contains("GROWTH_NARRATIVE")
                || marker.contains("LIFE_ARC_REFLECTION")
                || marker.contains("TRAIT_REFLECTION")
                || marker.contains("WEAKNESS_RECOVERY")
                || marker.contains("PERSONALITY_")
                || marker.contains("WORK_STYLE")
                || marker.contains("VALUES_REFLECTION");
    }

    private String buildPersonalStoryContext(List<Long> storyIds) {
        List<PersonalStory> stories = personalStoryRepository.findAllById(storyIds).stream()
                .filter(story -> !PersonalStory.WRITING_GUIDE_LEGACY_TYPE.equals(story.getType()))
                .sorted(java.util.Comparator.comparing(PersonalStory::getId))
                .toList();
        if (stories.isEmpty()) {
            return NO_VERIFIED_EXPERIENCE_CONTEXT;
        }

        StringBuilder sb = new StringBuilder("### LIFE STORY CONTEXT ###\n");
        sb.append("Use this as one continuous life story, not as separate answer candidates.\n");
        sb.append("Preserve the overall arc. If the question asks for a value, failure, turning point, strength, or period, expand only that matching point in detail and then return to the full arc.\n\n");
        for (PersonalStory story : stories) {
            if (PersonalStory.LIFE_STORY_TYPE.equals(story.getType())) {
                sb.append("[FULL_LIFE_STORY]\n");
            } else {
                sb.append("[LEGACY_STORY_CHUNK]\n");
            }
            sb.append(story.getContent()).append("\n---\n");
        }
        return sb.toString();
    }

    private String buildOthersContext(WorkspaceQuestion currentQuestion, Long questionId) {
        Long applicationId = currentQuestion.getApplication() == null ? null : currentQuestion.getApplication().getId();
        if (applicationId == null) {
            return "";
        }
        return questionRepository.findByApplicationIdOrderByIdAsc(applicationId).stream()
                .filter(q -> !q.getId().equals(questionId))
                .filter(q -> q.getContent() != null && !q.getContent().isBlank())
                .map(q -> String.format("[%s]\n%s",
                        q.getTitle(),
                        q.getContent().length() > 200
                                ? q.getContent().substring(0, 200) + "..."
                                : q.getContent()))
                .collect(Collectors.joining("\n---\n"));
    }

    private void logRagMatches(
            Long questionId,
            QuestionCategory category,
            List<com.resumade.api.workspace.dto.ExperienceContextResponse.ContextItem> results
    ) {
        String matches = results.stream()
                .limit(8)
                .map(item -> String.format("%s%s#%s score=%s",
                        firstNonBlank(item.getExperienceTitle(), "Untitled"),
                        item.getFacetTitle() == null || item.getFacetTitle().isBlank()
                                ? ""
                                : " / " + item.getFacetTitle(),
                        firstNonBlank(item.getId(), "-"),
                        item.getRelevanceScore()))
                .collect(Collectors.joining(" | "));
        log.info("[v3 RAG] questionId={} category={} matches={} candidates={}",
                questionId, category, results.size(), matches);
    }

    private void saveDraft(Long questionId, String draft) {
        WorkspaceQuestion question = questionRepository.findById(questionId).orElseThrow();
        question.setContent(draft);
        questionSnapshotService.saveSnapshot(questionId, SnapshotType.DRAFT_GENERATED, draft);
        questionRepository.save(question);
    }

    private void saveWashed(Long questionId, String washedKr, DraftAuthenticityReport report) throws Exception {
        WorkspaceQuestion question = questionRepository.findById(questionId).orElseThrow();
        question.setWashedKr(washedKr);
        question.setMistranslations("[]");
        question.setAiReview(objectMapper.writeValueAsString(buildAiReviewReport(report)));
        questionRepository.save(question);
        questionSnapshotService.saveSnapshot(questionId, SnapshotType.WASHED, washedKr);
    }

    private String buildDirective(WorkspaceQuestion question, boolean useDirective, Integer targetChars) {
        List<String> parts = new ArrayList<>();
        if (question.getBatchStrategyDirective() != null && !question.getBatchStrategyDirective().isBlank()) {
            parts.add(question.getBatchStrategyDirective().trim());
        }
        if (useDirective && question.getUserDirective() != null && !question.getUserDirective().isBlank()) {
            parts.add(question.getUserDirective().trim());
        }
        if (targetChars != null) {
            parts.add(String.format("목표 글자 수: %d자", targetChars));
        }
        return parts.isEmpty() ? "No extra user directive." : String.join("\n", parts);
    }

    private String assembleDraft(WorkspaceDraftAiService.DraftResponse response) {
        if (response == null) {
            return "";
        }
        String title = response.title == null ? "" : response.title.trim();
        String text = response.text == null ? "" : response.text.trim();
        if (title.isBlank()) {
            return text;
        }
        return "[" + title + "]\n\n" + text;
    }

    private String trimToHardLimit(String draft, int hardLimit) {
        String normalized = prepareWashed(draft);
        if (hardLimit <= 0 || countVisibleChars(normalized) <= hardLimit) {
            return normalized;
        }
        int endIndex = normalized.offsetByCodePoints(0, Math.min(hardLimit, normalized.codePointCount(0, normalized.length())));
        String trimmed = normalized.substring(0, endIndex).trim();
        int breakpoint = Math.max(
                Math.max(trimmed.lastIndexOf("\n"), trimmed.lastIndexOf(".")),
                Math.max(trimmed.lastIndexOf("!"), trimmed.lastIndexOf("?")));
        if (breakpoint >= Math.max(0, hardLimit - 80)) {
            return trimmed.substring(0, breakpoint + 1).trim();
        }
        return trimmed;
    }

    private String prepareWashed(String text) {
        if (text == null) {
            return "";
        }
        return text.trim()
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("\n{3,}", "\n\n");
    }

    private LengthPolicy resolveLengthPolicy(int maxLength, Integer targetChars) {
        if (maxLength <= 0) {
            return new LengthPolicy(1, 1, 1, false);
        }
        if (targetChars != null && targetChars > 0) {
            int desired = (int) Math.round(Math.min(Math.max(1, targetChars), maxLength));
            int lower = Math.max(1, (int) Math.ceil(desired * REQUESTED_TARGET_MIN_RATIO));
            return new LengthPolicy(Math.min(lower, desired), desired, maxLength, true);
        }
        int lower = Math.max(1, (int) Math.ceil(maxLength * TARGET_MIN_RATIO));
        int desired = Math.max(lower, (int) Math.round(maxLength * DEFAULT_DESIRED_TARGET_RATIO));
        return new LengthPolicy(lower, desired, maxLength, false);
    }

    private boolean isFinalLengthOk(int length, LengthPolicy policy) {
        return length >= policy.minTarget()
                && (policy.hardLimit() <= 0 || length <= policy.hardLimit());
    }

    private int countVisibleChars(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        int count = 0;
        for (int i = 0; i < normalized.length();) {
            int codePoint = normalized.codePointAt(i);
            i += Character.charCount(codePoint);
            if (codePoint == '\n') {
                count++;
                continue;
            }
            if (Character.isISOControl(codePoint) || Character.getType(codePoint) == Character.FORMAT) {
                continue;
            }
            count++;
        }
        return count;
    }

    private boolean isNoVerifiedExperienceContext(String experienceContext) {
        return experienceContext != null && experienceContext.contains("NO_VERIFIED_EXPERIENCE_CONTEXT");
    }

    private String toPlanJson(QuestionDraftPlanV3 plan) {
        try {
            return objectMapper.writeValueAsString(plan);
        } catch (Exception e) {
            return String.valueOf(plan);
        }
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return "";
    }

    private void sendProgress(SseEmitter emitter, String stage, String message) {
        Map<String, String> payload = new HashMap<>();
        payload.put("stage", stage);
        payload.put("message", message);
        sendStage(emitter, stage);
        sendSse(emitter, "progress", payload);
    }

    private void sendStage(SseEmitter emitter, String stage) {
        sendSse(emitter, "stage", Map.of("stage", stage));
    }

    private void sendSse(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(Utf8SseSupport.jsonEvent(name, data));
        } catch (IOException e) {
            if (isClientDisconnect(e)) {
                throw new SseConnectionClosedException(e);
            }
            log.warn("v3 SSE send failed name={} reason={}", name, e.getMessage());
        }
    }

    private void sendComment(SseEmitter emitter, String comment) {
        try {
            emitter.send(SseEmitter.event().comment(comment));
        } catch (IOException e) {
            if (isClientDisconnect(e)) {
                throw new SseConnectionClosedException(e);
            }
        }
    }

    private boolean isClientDisconnect(Exception e) {
        String message = e.getMessage();
        return message != null && (message.contains("Broken pipe")
                || message.contains("Connection reset")
                || message.contains("closed")
                || message.contains("aborted"));
    }

    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
        }
    }

    private HeartbeatHandle startHeartbeat(SseEmitter emitter) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (Exception ignored) {
            }
        }, HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL, TimeUnit.SECONDS);
        return new HeartbeatHandle(scheduler, future);
    }

    private String resolveErrorMessage(Exception e) {
        if (e instanceof IllegalArgumentException) {
            return e.getMessage();
        }
        return "서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";
    }

    private record HeartbeatHandle(ScheduledExecutorService scheduler, ScheduledFuture<?> future) {
        void stop() {
            future.cancel(false);
            scheduler.shutdownNow();
        }
    }

    private record LengthPolicy(int minTarget, int desiredTarget, int hardLimit, boolean hasRequestedTarget) {
    }

    private static class SseConnectionClosedException extends RuntimeException {
        SseConnectionClosedException(Throwable cause) {
            super(cause);
        }
    }
}
