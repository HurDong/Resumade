package com.resumade.api.workspace.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.experience.domain.Experience;
import com.resumade.api.experience.domain.ExperienceRepository;
import com.resumade.api.experience.domain.PersonalStory;
import com.resumade.api.experience.domain.PersonalStoryRepository;
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
import com.resumade.api.workspace.prompt.QuestionDraftPlan;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
 *   <li>Title rewrite — 완성 본문 기반 제목 전용 LLM 호출</li>
 *   <li>Wash — KO → EN → KO 번역 세탁</li>
 *   <li>Patch Analysis — 오역 감지 + 하이라이팅</li>
 * </ol>
 *
 * <p>기존 WorkspaceService.processHumanPatch()의 복잡한 길이 확장 루프는 명확한 2-Tier retry로 대체하고,
 * 제목은 본문 완성 후 별도 LLM 호출로 정제합니다.
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
    private static final double REQUESTED_TARGET_MIN_RATIO = 0.90;
    private static final double DEFAULT_DESIRED_TARGET_RATIO = 0.90;
    private static final double MINI_SHORTEN_MAX_RATIO = 1.20;
    private static final int    FINAL_LENGTH_RETRY_MAX_ATTEMPTS = 3;
    private static final int    LENGTH_REWRITE_MAX_ATTEMPTS = 5;
    private static final long   HEARTBEAT_INTERVAL  = 8L;
    private static final String NO_VERIFIED_EXPERIENCE_TITLE = "근거 경험 선택 필요";
    private static final String NO_VERIFIED_EXPERIENCE_MESSAGE =
            "이 문항에 연결할 검증된 경험이 아직 선택되거나 검색되지 않았습니다. 경험 보관소에서 관련 경험을 선택하거나 새 경험을 추가한 뒤 다시 생성해 주세요.";
    private static final String NO_VERIFIED_EXPERIENCE_CONTEXT = """
            NO_VERIFIED_EXPERIENCE_CONTEXT
            No RAG match or selected personal story was available for this question.
            Do not invent applicant incidents, metrics, tools, certifications, or roles.
            Return a short JSON answer asking the user to select or add a verified experience before drafting.
            """;

    private final WorkspaceQuestionRepository      questionRepository;
    private final ExperienceRepository             experienceRepository;
    private final PersonalStoryRepository          personalStoryRepository;
    private final ExperienceVectorRetrievalService experienceVectorRetrievalService;
    private final QuestionAnalysisService          questionAnalysisService;
    private final QuestionDraftPlannerService      questionDraftPlannerService;
    private final PromptFactory                    promptFactory;
    private final StrategyDraftGeneratorService    strategyDraftGeneratorService;
    private final DraftCriticRewriteService        draftCriticRewriteService;
    private final DraftQualityCheckService         draftQualityCheckService;
    private final TranslationService               translationService;
    private final WorkspaceDraftAiService          workspaceDraftAiService;
    private final WorkspaceTitleService            workspaceTitleService;
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
            log.info("[■ 스트림/종료] 파이프라인=초안V2 | 문항={} | 사유=클라이언트종료", questionId);
        } catch (Exception e) {
            log.error("[!! 파이프라인/실패] 파이프라인=초안V2 | 문항={}", questionId, e);
            workspaceTaskCache.setError(questionId, resolveErrorMessage(e));
            try {
                sendSse(emitter, "error", resolveErrorMessage(e));
            } catch (SseConnectionClosedException ignored) {
                log.info("[■ 스트림/종료] 파이프라인=초안V2 | 상태=오류보고중이미종료");
            }
            completeQuietly(emitter);
        } finally {
            heartbeat.stop();
        }
    }

    @Transactional
    public void processRefinementV2(Long questionId, String directive, Integer targetChars,
                                    List<Long> storyIds, SseEmitter emitter) {
        HeartbeatHandle heartbeat = startHeartbeat(emitter);
        workspaceTaskCache.setRunning(questionId);
        try {
            runRefinement(questionId, directive, targetChars, storyIds, emitter);
        } catch (SseConnectionClosedException e) {
            log.info("[■ 스트림/종료] 파이프라인=다듬기V2 | 문항={} | 사유=클라이언트종료", questionId);
        } catch (Exception e) {
            log.error("[!! 파이프라인/실패] 파이프라인=다듬기V2 | 문항={}", questionId, e);
            workspaceTaskCache.setError(questionId, resolveErrorMessage(e));
            try {
                sendSse(emitter, "error", resolveErrorMessage(e));
            } catch (SseConnectionClosedException ignored) {
                log.info("[■ 스트림/종료] 파이프라인=다듬기V2 | 상태=오류보고중이미종료");
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

        if (runPlanBasedDraftPipeline(question, questionId, company, position, questionTitle,
                maxLength, useDirective, targetChars, storyIds, emitter)) {
            return;
        }

        // ── STAGE 1: Question Analysis ─────────────────────────────────────
        sendProgress(emitter, STAGE_ANALYSIS, "문항의 의도와 요구 구조를 분석하고 있습니다. 🧠");
        QuestionProfile profile = questionAnalysisService.analyze(questionTitle);
        log.info("[◆ 초안V2/분석] 문항={} | 카테고리={} | 복합문항={} | 필수요소={}개",
                questionId, profile.primaryCategory(), profile.isCompound(),
                profile.requiredElements().size());
        sendProgress(emitter, STAGE_ANALYSIS,
                String.format("문항 분석 완료 (%s%s). 맞춤 전략을 적용합니다. 🎯",
                        profile.primaryCategory().getDisplayName(),
                        profile.isCompound() ? " · 복합 문항" : ""));

        // ── STAGE 2: RAG ────────────────────────────────────────────────────
        sendProgress(emitter, STAGE_RAG, "기업 데이터와 경험 볼트에서 관련 자료를 찾고 있습니다. 🧭");
        String companyContext    = buildCompanyContext(question);
        String experienceContext = buildExperienceContext(question, questionId, profile, storyIds);
        String othersContext     = buildOthersContext(question, questionId);

        LengthPolicy lengthPolicy = resolveLengthPolicy(maxLength, targetChars);
        int minTarget    = lengthPolicy.minTarget();
        int preferredMax = lengthPolicy.desiredTarget();

        if (isNoVerifiedExperienceContext(experienceContext)) {
            log.warn("[!! RAG/근거없음] 문항={} | 카테고리={} | 처리=초안생성중단 | 안내=경험선택필요",
                    questionId, profile.primaryCategory());
            sendProgress(emitter, STAGE_RAG, "이 문항에 연결할 검증된 경험을 찾지 못해 초안 생성을 중단했습니다.");
            completeWithNoVerifiedExperience(emitter, questionId, maxLength, minTarget, preferredMax);
            return;
        }

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
        try {
            draft = fitDraftLengthBeforeWash(profile, params, draft, lengthPolicy, emitter);
        } catch (DraftLengthFitException e) {
            draft = e.bestDraft();
            saveDraft(questionId, draft);
            sendSse(emitter, "draft_intermediate", draft);
            completeWithDraftLengthFailed(emitter, questionId, draft, maxLength, minTarget, preferredMax, e.getMessage());
            return;
        }

        sendProgress(emitter, STAGE_DRAFT, "완성된 본문을 바탕으로 제목을 별도 생성하고 있습니다. 🏷️");
        draft = workspaceTitleService.rewriteTitleFromDraft(new WorkspaceTitleService.TitleRewriteRequest(
                draft,
                company,
                position,
                questionTitle,
                profile.primaryCategory(),
                profile,
                companyContext,
                experienceContext,
                othersContext,
                directive));

        saveDraft(questionId, draft);
        sendSse(emitter, "draft_intermediate", draft);

        // ── STAGE 4: Wash ────────────────────────────────────────────────────
        sendProgress(emitter, STAGE_WASH, "초안 고도화를 위해 중간 번역 과정을 거치고 있습니다. 🌐");
        String washedKr;
        try {
            String translatedEn = translationService.translateToEnglish(draft);
            sendProgress(emitter, STAGE_WASH, "한국어로 다시 번역하며 표현을 더 자연스럽게 다듬고 있습니다. 🫧");
            washedKr = prepareWashed(translationService.translateToKorean(translatedEn));
        } catch (Exception e) {
            log.warn("[!! 세탁/실패] 문항={} | 처리=원문초안반환 | 사유={}", questionId, e.getMessage());
            completeWithWashFailed(emitter, questionId, draft, maxLength, minTarget);
            return;
        }

        question = questionRepository.findById(questionId).orElseThrow();
        question.setWashedKr(washedKr);
        questionRepository.save(question);
        questionSnapshotService.saveSnapshot(questionId, SnapshotType.WASHED, washedKr);
        sendSse(emitter, "washed_intermediate", washedKr);

        // ── STAGE 5: Patch Analysis ───────────────────────────────────────
        sendProgress(emitter, STAGE_PATCH, "원본과 번역본을 비교하며 오역을 검수하고 있습니다. 🔎");
        finalizePatch(emitter, questionId, draft, washedKr, maxLength, minTarget, preferredMax, params);
    }

    private boolean runPlanBasedDraftPipeline(
            WorkspaceQuestion question,
            Long questionId,
            String company,
            String position,
            String questionTitle,
            int maxLength,
            boolean useDirective,
            Integer targetChars,
            List<Long> storyIds,
            SseEmitter emitter
    ) throws Exception {
        LengthPolicy lengthPolicy = resolveLengthPolicy(maxLength, targetChars);
        int minTarget = lengthPolicy.minTarget();
        int preferredMax = lengthPolicy.desiredTarget();
        int draftHardLimit = maxLength;
        String directive = buildDirective(question, useDirective, maxLength, targetChars);

        sendProgress(emitter, STAGE_ANALYSIS, "문항 의도와 답변 설계안을 생성하고 있습니다.");
        QuestionDraftPlan plan = questionDraftPlannerService.plan(
                company, position, questionTitle, draftHardLimit, minTarget, preferredMax, directive);
        QuestionProfile profile = plan.toProfile();
        log.info("[◆ 초안V2/설계] 문항={} | 카테고리={} | 의도={} | 작성자세={} | 문단={} | RAG={} | 목표={}~{}자 | 제한={}자",
                questionId, profile.primaryCategory(), plan.questionIntent(), plan.answerPosture(),
                plan.paragraphCount(), plan.experienceNeeds().size(), minTarget, preferredMax, maxLength);

        sendProgress(emitter, STAGE_RAG, "설계안에 맞는 경험 단위를 검색하고 있습니다.");
        String companyContext = buildCompanyContext(question);
        String experienceContext = buildExperienceContext(question, questionId, plan, storyIds);
        String othersContext = buildOthersContext(question, questionId);

        if (isNoVerifiedExperienceContext(experienceContext)) {
            log.warn("[!! RAG/근거없음] 문항={} | 카테고리={} | 의도={} | RAG요청={} | 처리=초안생성중단 | 안내=경험선택필요",
                    questionId, profile.primaryCategory(), plan.questionIntent(), plan.experienceNeeds().size());
            sendProgress(emitter, STAGE_RAG, "이 문항에 연결할 검증된 경험을 찾지 못해 초안 생성을 중단했습니다.");
            completeWithNoVerifiedExperience(emitter, questionId, maxLength, minTarget, preferredMax);
            return true;
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
                .maxLength(draftHardLimit)
                .minTarget(minTarget)
                .maxTarget(preferredMax)
                .build();

        sendProgress(emitter, STAGE_DRAFT, "답변 설계안과 경험 근거를 바탕으로 초안을 작성하고 있습니다.");
        WorkspaceDraftAiService.DraftResponse generated = strategyDraftGeneratorService.generate(
                promptFactory.buildDraftPlanMessages(plan, params));

        sendProgress(emitter, STAGE_DRAFT, "질문 충족도와 문단 압축 상태를 검수해 필요한 부분만 고치고 있습니다.");
        WorkspaceDraftAiService.DraftResponse rewritten = draftCriticRewriteService.rewrite(params, generated);
        if (rewritten != null && rewritten.text != null && countVisibleChars(rewritten.text) > draftHardLimit) {
            rewritten.text = hardTrimToLimit(rewritten.text, draftHardLimit);
        }
        String draft = assembleDraft(rewritten);
        draft = enforceDraftGroundingIfNeeded(questionId, params, draft, "초안");

        sendProgress(emitter, STAGE_WASH, "초안 문체를 번역 왕복으로 세탁하고, 최종 글자 수를 검증하고 있습니다.");
        WashedDraftCandidate bestCandidate;
        try {
            bestCandidate = washAndFitFinalLength(questionId, draft, params, lengthPolicy, emitter);
        } catch (Exception e) {
            log.warn("[!! 세탁/실패] 문항={} | 처리=원문초안반환 | 사유={}", questionId, e.getMessage());
            completeWithWashFailed(emitter, questionId, draft, maxLength, minTarget);
            return true;
        }

        draft = bestCandidate.sourceDraft();
        String washedKr = bestCandidate.washedDraft();
        saveDraft(questionId, draft);
        sendSse(emitter, "draft_intermediate", draft);

        question = questionRepository.findById(questionId).orElseThrow();
        question.setWashedKr(washedKr);
        questionRepository.save(question);
        questionSnapshotService.saveSnapshot(questionId, SnapshotType.WASHED, washedKr);
        sendSse(emitter, "washed_intermediate", washedKr);

        completeWithoutPatch(emitter, questionId, draft, washedKr, maxLength, minTarget, preferredMax);
        return true;
    }

    private WashedDraftCandidate washAndFitFinalLength(
            Long questionId,
            String initialDraft,
            DraftParams params,
            LengthPolicy policy,
            SseEmitter emitter
    ) throws Exception {
        List<WashedDraftCandidate> candidateCache = new ArrayList<>();
        WashedDraftCandidate best = washDraftCandidate(initialDraft, 0, policy);
        candidateCache.add(best);
        logWashedCandidate("초기 세탁", questionId, best, policy, true);

        if (best.lengthOk()) {
            log.info("[● 길이/통과] 문항={} | 회차=초기 | 최종={}자 | 목표={}~{}자",
                    questionId, best.washedLength(), policy.minTarget(), policy.hardLimit());
            return best;
        }

        for (int attempt = 1; attempt <= FINAL_LENGTH_RETRY_MAX_ATTEMPTS; attempt++) {
            sendProgress(emitter, STAGE_DRAFT,
                    String.format("세탁본 글자 수가 목표 범위를 벗어나 재작성하고 있습니다 (%d/%d). 현재 최선: %d자",
                            attempt, FINAL_LENGTH_RETRY_MAX_ATTEMPTS, best.washedLength()));
            log.warn("[▲ 길이/재시도] 문항={} | 회차={}/{} | 현재최선={}자 | 목표={}~{}자 | 점수={} | 다음=재작성",
                    questionId,
                    attempt, FINAL_LENGTH_RETRY_MAX_ATTEMPTS,
                    best.washedLength(), policy.minTarget(), policy.hardLimit(), best.score());

            WorkspaceDraftAiService.DraftResponse retryResponse = draftCriticRewriteService.rewriteForFinalLength(
                    params,
                    best.sourceDraft(),
                    best.washedDraft(),
                    best.washedLength(),
                    attempt,
                    FINAL_LENGTH_RETRY_MAX_ATTEMPTS);
            String retryDraft = assembleDraft(retryResponse);
            if (retryDraft.isBlank()) {
                log.warn("[!! 길이/재시도] 문항={} | 회차={}/{} | 상태=빈결과 | 처리=후보건너뜀",
                        questionId, attempt, FINAL_LENGTH_RETRY_MAX_ATTEMPTS);
                continue;
            }
            if (countVisibleChars(retryDraft) > policy.hardLimit()) {
                retryDraft = hardTrimToLimit(retryDraft, policy.hardLimit());
                log.warn("[▲ 길이/절단] 문항={} | 회차={}/{} | 사유=제한초과 | 제한={}자",
                        questionId, attempt, FINAL_LENGTH_RETRY_MAX_ATTEMPTS, policy.hardLimit());
            }

            retryDraft = enforceDraftGroundingIfNeeded(questionId, params, retryDraft, "길이재시도-" + attempt);

            WashedDraftCandidate candidate;
            try {
                candidate = washDraftCandidate(retryDraft, attempt, policy);
            } catch (Exception e) {
                log.warn("[!! 길이/세탁실패] 문항={} | 회차={}/{} | 처리=다음재시도 | 사유={}",
                        questionId, attempt, FINAL_LENGTH_RETRY_MAX_ATTEMPTS, e.getMessage());
                continue;
            }

            candidateCache.add(candidate);
            boolean improved = candidate.isBetterThan(best);
            if (improved) {
                best = candidate;
            }
            logWashedCandidate("재시도 " + attempt, questionId, candidate, policy, improved);

            if (candidate.lengthOk()) {
                log.info("[● 길이/통과] 문항={} | 회차={}/{} | 최종={}자 | 목표={}~{}자 | 후보={}개",
                        questionId, attempt, FINAL_LENGTH_RETRY_MAX_ATTEMPTS,
                        candidate.washedLength(), policy.minTarget(), policy.hardLimit(), candidateCache.size());
                return candidate;
            }
        }

        log.warn("[!! 길이/최선반환] 문항={} | 사유=재시도소진 | 후보={}개 | 최선={}자 | 점수={} | 목표={}~{}자",
                questionId, candidateCache.size(), best.washedLength(), best.score(), policy.minTarget(), policy.hardLimit());
        return best;
    }

    private WashedDraftCandidate washDraftCandidate(String sourceDraft, int attempt, LengthPolicy policy) throws Exception {
        String translatedEn = translationService.translateToEnglish(sourceDraft);
        String washedKr = prepareWashed(translationService.translateToKorean(translatedEn));
        int sourceLength = countVisibleChars(sourceDraft);
        int washedLength = countVisibleChars(washedKr);
        boolean lengthOk = isFinalLengthOk(washedLength, policy);
        int score = finalLengthScore(washedLength, policy);
        return new WashedDraftCandidate(sourceDraft, washedKr, sourceLength, washedLength, score, attempt, lengthOk);
    }

    private void logWashedCandidate(
            String label,
            Long questionId,
            WashedDraftCandidate candidate,
            LengthPolicy policy,
            boolean cachedAsBest
    ) {
        String status = candidate.lengthOk() ? "통과" : "실패";
        String cache = cachedAsBest ? "최선후보 갱신" : "기존 최선 유지";
        log.info("[▲ 길이/후보] 단계={} | 문항={} | 상태={} | 원문={}자 | 세탁본={}자 | 목표={}~{}자 | 선호={}자 | 점수={} | 캐시={}",
                label, questionId, status,
                candidate.sourceLength(), candidate.washedLength(),
                policy.minTarget(), policy.hardLimit(), policy.desiredTarget(),
                candidate.score(), cache);
    }

    private void runRefinement(Long questionId, String userDirective, Integer targetChars,
                               List<Long> storyIds, SseEmitter emitter) throws Exception {

        WorkspaceQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found: " + questionId));

        String currentDraft = firstNonBlank(question.getWashedKr(), question.getContent());
        if (currentDraft.isBlank()) {
            throw new IllegalStateException("No existing draft to refine for question: " + questionId);
        }

        String company       = question.getApplication().getCompanyName();
        String position      = question.getApplication().getPosition();
        String questionTitle = question.getTitle();
        int    maxLength     = question.getMaxLength();

        sendComment(emitter, "flush buffer");

        sendProgress(emitter, STAGE_ANALYSIS, "문항의 의도와 현재 초안의 수정 방향을 분석하고 있습니다. 🧠");
        QuestionProfile profile = questionAnalysisService.analyze(questionTitle);
        log.info("[◆ 다듬기V2/분석] 문항={} | 카테고리={} | 복합문항={} | 필수요소={}개",
                questionId, profile.primaryCategory(), profile.isCompound(),
                profile.requiredElements().size());
        sendProgress(emitter, STAGE_ANALYSIS,
                String.format("문항 분석 완료 (%s%s). v2 다듬기 전략을 적용합니다. 🎯",
                        profile.primaryCategory().getDisplayName(),
                        profile.isCompound() ? " · 복합 문항" : ""));

        sendProgress(emitter, STAGE_RAG, "기업 데이터와 경험 볼트에서 다듬기에 필요한 근거를 다시 확인하고 있습니다. 🧭");
        String companyContext    = buildCompanyContext(question);
        String experienceContext = buildExperienceContext(question, questionId, profile, storyIds);
        String othersContext     = buildOthersContext(question, questionId);

        LengthPolicy lengthPolicy = resolveLengthPolicy(maxLength, targetChars);
        int minTarget    = lengthPolicy.minTarget();
        int preferredMax = lengthPolicy.desiredTarget();

        if (isNoVerifiedExperienceContext(experienceContext)) {
            log.warn("[!! RAG/근거없음] 단계=다듬기V2 | 문항={} | 카테고리={} | 처리=다듬기중단 | 안내=경험선택필요",
                    questionId, profile.primaryCategory());
            sendProgress(emitter, STAGE_RAG, "다듬기에 사용할 검증된 경험을 찾지 못해 작업을 중단했습니다.");
            completeWithNoVerifiedExperience(emitter, questionId, maxLength, minTarget, preferredMax);
            return;
        }

        String directive = buildRefinementDirective(question, userDirective, maxLength, targetChars);
        sendProgress(emitter, STAGE_RAG, "기존 초안과 겹치지 않는 보강 포인트를 선별했습니다. 🧩");

        sendProgress(emitter, STAGE_DRAFT, "현재 초안을 기준으로 요청 사항을 반영해 다시 다듬고 있습니다. ✍️");
        DraftParams params = DraftParams.builder()
                .company(company).position(position).questionTitle(questionTitle)
                .companyContext(companyContext).experienceContext(experienceContext)
                .othersContext(othersContext).directive(directive)
                .maxLength(maxLength).minTarget(minTarget).maxTarget(preferredMax)
                .build();

        String draft = refineWithRetry(profile, params, currentDraft, minTarget, preferredMax, emitter);
        try {
            draft = fitDraftLengthBeforeWash(profile, params, draft, lengthPolicy, emitter);
        } catch (DraftLengthFitException e) {
            draft = e.bestDraft();
            saveDraft(questionId, draft, userDirective);
            sendSse(emitter, "draft_intermediate", draft);
            completeWithDraftLengthFailed(emitter, questionId, draft, maxLength, minTarget, preferredMax, e.getMessage());
            return;
        }

        sendProgress(emitter, STAGE_DRAFT, "다듬은 본문을 바탕으로 제목을 다시 정리하고 있습니다. 🏷️");
        draft = workspaceTitleService.rewriteTitleFromDraft(new WorkspaceTitleService.TitleRewriteRequest(
                draft,
                company,
                position,
                questionTitle,
                profile.primaryCategory(),
                profile,
                companyContext,
                experienceContext,
                othersContext,
                directive));

        saveDraft(questionId, draft, userDirective);
        sendSse(emitter, "draft_intermediate", draft);

        sendProgress(emitter, STAGE_WASH, "다듬은 초안을 번역 세탁하고 있습니다. 🌐");
        String washedKr;
        try {
            String translatedEn = translationService.translateToEnglish(draft);
            sendProgress(emitter, STAGE_WASH, "한국어로 다시 번역하며 표현을 자연스럽게 정리하고 있습니다. 🫧");
            washedKr = prepareWashed(translationService.translateToKorean(translatedEn));
        } catch (Exception e) {
            log.warn("[!! 다듬기V2/세탁실패] 문항={} | 처리=다듬은초안반환 | 사유={}", questionId, e.getMessage());
            completeWithWashFailed(emitter, questionId, draft, maxLength, minTarget);
            return;
        }

        question = questionRepository.findById(questionId).orElseThrow();
        question.setWashedKr(washedKr);
        question.setFinalText(null);
        questionRepository.save(question);
        questionSnapshotService.saveSnapshot(questionId, SnapshotType.WASHED, washedKr);
        sendSse(emitter, "washed_intermediate", washedKr);

        sendProgress(emitter, STAGE_PATCH, "원본과 세탁본을 비교하며 오역을 검수하고 있습니다. 🔎");
        finalizePatch(emitter, questionId, draft, washedKr, maxLength, minTarget, preferredMax, params);
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
            DraftQualityResult quality = draftQualityCheckService.check(draft, profile, minTarget, preferredMax);
            if (quality.passed()) {
                return draft;
            }

            log.info("[▲ 품질/재시도] 단계=초안 | 회차={}/{} | 길이통과={} | 요소통과={}",
                    attempt, MAX_RETRIES, quality.lengthOk(), quality.elementsOk());
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

    private String refineWithRetry(QuestionProfile profile, DraftParams params, String currentDraft,
                                   int minTarget, int preferredMax, SseEmitter emitter) {
        List<ChatMessage> messages = promptFactory.buildRefineMessagesV2(
                profile,
                params,
                currentDraft,
                "Revise the current draft according to the user directive while preserving verified facts.");
        WorkspaceDraftAiService.DraftResponse resp = strategyDraftGeneratorService.generate(messages);
        String draft = assembleDraft(resp);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            DraftQualityResult quality = draftQualityCheckService.check(draft, profile, minTarget, preferredMax);
            if (quality.passed()) {
                return draft;
            }

            log.info("[▲ 품질/재시도] 단계=다듬기 | 회차={}/{} | 길이통과={} | 요소통과={}",
                    attempt, MAX_RETRIES, quality.lengthOk(), quality.elementsOk());
            sendProgress(emitter, STAGE_DRAFT,
                    String.format("다듬은 초안의 품질을 다시 보강하고 있습니다 (%d/%d). 🔄", attempt, MAX_RETRIES));

            List<ChatMessage> refineMessages = promptFactory.buildRefineMessagesV2(
                    profile, params, draft, quality.retryDirective());
            resp = strategyDraftGeneratorService.generate(refineMessages);
            draft = assembleDraft(resp);
        }

        return draft;
    }

    private String assembleDraft(WorkspaceDraftAiService.DraftResponse resp) {
        if (resp == null) return "";
        String title = resp.title != null ? resp.title.trim() : "";
        String text  = resp.text  != null ? resp.text.trim()  : "";
        if (title.isBlank()) return text;
        return "[" + title + "]" + "\n\n" + text;
    }

    private String enforceCoverLetterStyleIfNeeded(Long questionId, DraftParams params, String draft, String stage) {
        if (!looksReportStyle(draft)) {
            return draft;
        }
        log.warn("[▲ 문체/보고서감지] 문항={} | 단계={} | 처리=자소서문체재작성", questionId, stage);
        WorkspaceDraftAiService.DraftResponse rewritten = draftCriticRewriteService.rewriteReportStyle(params, draft);
        String repaired = assembleDraft(rewritten);
        if (repaired.isBlank()) {
            log.warn("[!! 문체/재작성실패] 문항={} | 단계={} | 처리=기존초안유지", questionId, stage);
            return draft;
        }
        if (looksReportStyle(repaired)) {
            log.warn("[!! 문체/보고서잔존] 문항={} | 단계={} | 처리=재작성본사용", questionId, stage);
        } else {
            log.info("[● 문체/자소서화] 문항={} | 단계={} | 처리=재작성완료", questionId, stage);
        }
        return repaired;
    }

    private String enforceDraftGroundingIfNeeded(Long questionId, DraftParams params, String draft, String stage) {
        String repaired = enforceCoverLetterStyleIfNeeded(questionId, params, draft, stage);
        if (!hasUnsupportedOperationalFacts(repaired, params.experienceContext())) {
            return repaired;
        }

        log.warn("[▲ 팩트/근거없는운영사례감지] 문항={} | 단계={} | 처리=검증팩트기반재작성", questionId, stage);
        WorkspaceDraftAiService.DraftResponse rewritten = draftCriticRewriteService.rewriteReportStyle(params, repaired);
        String grounded = assembleDraft(rewritten);
        if (grounded.isBlank()) {
            log.warn("[!! 팩트/재작성실패] 문항={} | 단계={} | 처리=기존초안유지", questionId, stage);
            return repaired;
        }
        if (hasUnsupportedOperationalFacts(grounded, params.experienceContext())) {
            log.warn("[!! 팩트/근거없는운영사례잔존] 문항={} | 단계={} | 처리=재작성본사용", questionId, stage);
        } else {
            log.info("[● 팩트/검증근거정렬] 문항={} | 단계={} | 처리=재작성완료", questionId, stage);
        }
        return grounded;
    }

    private boolean looksReportStyle(String draft) {
        if (draft == null || draft.isBlank()) {
            return false;
        }
        String normalized = draft.toLowerCase(Locale.ROOT);
        return normalized.contains("핵심 역량은 다음")
                || normalized.contains("다음과 같습니다")
                || normalized.contains("주요 경력")
                || normalized.contains("관련 경험")
                || normalized.contains("역할, 조치, 결과")
                || normalized.contains("역할, 수행 내용, 결과")
                || normalized.contains("사고 대응")
                || normalized.contains("rca")
                || normalized.contains("mttr")
                || normalized.contains("1)")
                || normalized.contains("2)")
                || normalized.contains("3)");
    }

    private boolean hasUnsupportedOperationalFacts(String draft, String experienceContext) {
        if (draft == null || draft.isBlank()) {
            return false;
        }
        String normalizedDraft = draft.toLowerCase(Locale.ROOT);
        String normalizedContext = experienceContext == null ? "" : experienceContext.toLowerCase(Locale.ROOT);

        int unsupported = 0;
        unsupported += unsupportedIfPresent(normalizedDraft, normalizedContext, "복제 지연");
        unsupported += unsupportedIfPresent(normalizedDraft, normalizedContext, "복제");
        unsupported += unsupportedIfPresent(normalizedDraft, normalizedContext, "8시간");
        unsupported += unsupportedIfPresent(normalizedDraft, normalizedContext, "dba");
        unsupported += unsupportedIfPresent(normalizedDraft, normalizedContext, "읽기 전용");
        unsupported += unsupportedIfPresent(normalizedDraft, normalizedContext, "재동기화");
        unsupported += unsupportedIfPresent(normalizedDraft, normalizedContext, "rto");
        unsupported += unsupportedIfPresent(normalizedDraft, normalizedContext, "rpo");
        unsupported += unsupportedIfPresent(normalizedDraft, normalizedContext, "무결성 검사");
        unsupported += unsupportedIfPresent(normalizedDraft, normalizedContext, "경영진");
        return unsupported >= 2;
    }

    private int unsupportedIfPresent(String draft, String context, String token) {
        return draft.contains(token) && !context.contains(token) ? 1 : 0;
    }

    private void saveDraft(Long questionId, String draft) {
        WorkspaceQuestion question = questionRepository.findById(questionId).orElseThrow();
        question.setContent(draft);
        questionSnapshotService.saveSnapshot(questionId, SnapshotType.DRAFT_GENERATED, draft);
        questionRepository.save(question);
    }

    private void saveDraft(Long questionId, String draft, String userDirective) {
        WorkspaceQuestion question = questionRepository.findById(questionId).orElseThrow();
        question.setContent(draft);
        question.setUserDirective(userDirective);
        questionSnapshotService.saveSnapshot(questionId, SnapshotType.DRAFT_GENERATED, draft);
        questionRepository.save(question);
    }

    private String fitDraftLengthBeforeWash(QuestionProfile profile, DraftParams params, String draft,
                                            LengthPolicy policy, SseEmitter emitter) {
        String candidate = normalizeLengthText(draft).trim();
        String bestCandidate = candidate;
        int bestScore = lengthDistance(countVisibleChars(candidate), policy);

        for (int attempt = 1; attempt <= LENGTH_REWRITE_MAX_ATTEMPTS + 1; attempt++) {
            int currentLength = countVisibleChars(candidate);
            int currentScore = lengthDistance(currentLength, policy);
            if (currentScore < bestScore) {
                bestCandidate = candidate;
                bestScore = currentScore;
            }

            LengthDecision decision = decideLengthAction(currentLength, policy);
            if (decision == LengthDecision.ACCEPT) {
                log.debug("[● 초안길이/통과] 현재={}자 | 목표={}~{}자", currentLength, policy.minTarget(), policy.desiredTarget());
                return candidate;
            }

            if (decision == LengthDecision.MINI_SHORTEN) {
                String shortened = shortenWithMini(candidate, policy, params);
                int shortenedLength = countVisibleChars(shortened);
                int shortenedScore = lengthDistance(shortenedLength, policy);
                if (!shortened.isBlank() && shortenedScore < bestScore) {
                    bestCandidate = shortened;
                    bestScore = shortenedScore;
                }
                if (!shortened.isBlank()
                        && shortenedLength >= policy.minTarget()
                        && shortenedLength <= policy.desiredTarget()) {
                    log.info("[■ 초안길이/압축] 이전={}자 | 이후={}자 | 목표={}~{}자",
                            currentLength, shortenedLength, policy.minTarget(), policy.desiredTarget());
                    return shortened;
                }

                log.warn("[▲ 초안길이/압축미달] 회차={}/{} | 이전={}자 | 이후={}자 | 목표={}~{}자 | 다음=재작성",
                        attempt, LENGTH_REWRITE_MAX_ATTEMPTS,
                        currentLength, shortenedLength, policy.minTarget(), policy.desiredTarget());
                candidate = !shortened.isBlank() ? shortened : candidate;
                decision = LengthDecision.REWRITE;
            }

            if (attempt > LENGTH_REWRITE_MAX_ATTEMPTS) {
                break;
            }

            log.warn("[▲ 초안길이/재작성] 회차={}/{} | 현재={}자 | 목표={}~{}자 | 제한={}자",
                    attempt, LENGTH_REWRITE_MAX_ATTEMPTS,
                    countVisibleChars(candidate), policy.minTarget(), policy.desiredTarget(), policy.hardLimit());
            sendProgress(emitter, STAGE_DRAFT,
                    String.format("초안 글자수를 목표 범위에 맞춰 다시 작성하고 있습니다 (%d/%d).",
                            attempt, LENGTH_REWRITE_MAX_ATTEMPTS));

            List<ChatMessage> refineMessages = promptFactory.buildRefineMessagesV2(
                    profile, params, candidate, buildLengthRewriteDirective(candidate, policy, decision));
            WorkspaceDraftAiService.DraftResponse resp = strategyDraftGeneratorService.generate(refineMessages);
            candidate = assembleDraft(resp);
        }

        int finalLength = countVisibleChars(candidate);
        int bestLength = countVisibleChars(bestCandidate);
        log.warn("[!! 초안길이/실패] 최종={}자 | 최선={}자 | 목표={}~{}자 | 제한={}자",
                finalLength, bestLength, policy.minTarget(), policy.desiredTarget(), policy.hardLimit());
        throw new DraftLengthFitException(String.format(
                "초안이 목표 구간(%d-%d자)에 맞지 않아 세탁을 중단했습니다. 가장 가까운 초안(%d자)을 저장했습니다.",
                policy.minTarget(), policy.desiredTarget(), bestLength), bestCandidate);
    }

    private int lengthDistance(int length, LengthPolicy policy) {
        if (length < policy.minTarget()) {
            return policy.minTarget() - length;
        }
        if (length > policy.desiredTarget()) {
            return length - policy.desiredTarget();
        }
        return 0;
    }

    private boolean isFinalLengthOk(int length, LengthPolicy policy) {
        return length >= policy.minTarget()
                && (policy.hardLimit() <= 0 || length <= policy.hardLimit());
    }

    private int finalLengthScore(int length, LengthPolicy policy) {
        int outsidePenalty = 0;
        if (length < policy.minTarget()) {
            outsidePenalty = policy.minTarget() - length;
        } else if (policy.hardLimit() > 0 && length > policy.hardLimit()) {
            outsidePenalty = length - policy.hardLimit();
        }
        return (outsidePenalty * 1000) + Math.abs(length - policy.desiredTarget());
    }

    private LengthDecision decideLengthAction(int currentLength, LengthPolicy policy) {
        if (currentLength >= policy.minTarget() && currentLength <= policy.desiredTarget()) {
            return LengthDecision.ACCEPT;
        }
        if (currentLength < policy.minTarget()) {
            return LengthDecision.REWRITE;
        }
        double ratio = policy.desiredTarget() > 0
                ? (double) currentLength / (double) policy.desiredTarget()
                : Double.POSITIVE_INFINITY;
        return ratio <= MINI_SHORTEN_MAX_RATIO
                ? LengthDecision.MINI_SHORTEN
                : LengthDecision.REWRITE;
    }

    private String shortenWithMini(String text, LengthPolicy policy, DraftParams params) {
        int currentLength = countVisibleChars(text);
        try {
            WorkspaceDraftAiService.DraftResponse shortened = workspaceDraftAiService.shortenToLimit(
                    params.company(),
                    params.position(),
                    params.companyContext(),
                    text,
                    policy.desiredTarget(),
                    params.experienceContext(),
                    params.othersContext());
            return shortened != null && shortened.text != null
                    ? normalizeLengthText(shortened.text).trim()
                    : "";
        } catch (Exception e) {
            log.warn("[!! 초안길이/미니압축실패] 현재={}자 | 사유={}", currentLength, e.getMessage());
            return "";
        }
    }

    private String buildLengthRewriteDirective(String text, LengthPolicy policy, LengthDecision reason) {
        int currentLength = countVisibleChars(text);
        return """
                The previous draft failed the length policy. This is a full rewrite, not a minor shorten.
                Failure reason: %s
                Current length: %d visible characters
                Required body range: %d to %d visible characters
                Hard limit: %d visible characters

                Rewrite from the same facts and question intent.
                Use a compact structure from the start.
                Use only one main example.
                Keep the title separate and do not repeat it in the body.
                The body must land inside the required range before translation.
                """.formatted(
                reason,
                currentLength,
                policy.minTarget(),
                policy.desiredTarget(),
                policy.hardLimit());
    }

    private String formatRatio(int currentLength, int targetLength) {
        if (targetLength <= 0) {
            return "n/a";
        }
        return String.format("%.2f", (double) currentLength / (double) targetLength);
    }

    private String enforceUpperLimit(String text, int maxLength, int minTarget, int preferredMax,
                                     DraftParams params, String stage) {
        String normalized = normalizeLengthText(text).trim();
        int currentLength = countVisibleChars(normalized);
        if (maxLength <= 0 || currentLength <= maxLength) {
            return normalized;
        }

        log.warn("[▲ 초안길이/제한초과] 단계={} | 현재={}자 | 제한={}자 | 다음=압축",
                stage, currentLength, maxLength);
        try {
            WorkspaceDraftAiService.DraftResponse shortened = workspaceDraftAiService.shortenToLimit(
                    params.company(),
                    params.position(),
                    params.companyContext(),
                    normalized,
                    maxLength,
                    params.experienceContext(),
                    params.othersContext());

            String candidate = shortened != null && shortened.text != null
                    ? normalizeLengthText(shortened.text).trim()
                    : "";
            int candidateLength = countVisibleChars(candidate);
            if (!candidate.isBlank() && candidateLength <= maxLength) {
                return candidate;
            }

            log.warn("[▲ 초안길이/압축부족] 단계={} | 압축후={}자 | 제한={}자 | 다음=강제절단",
                    stage, candidateLength, maxLength);
            return hardTrimToLimit(!candidate.isBlank() ? candidate : normalized, maxLength);
        } catch (Exception e) {
            log.warn("[!! 초안길이/압축실패] 단계={} | 현재={}자 | 제한={}자 | 처리=강제절단 | 사유={}",
                    stage, currentLength, maxLength, e.getMessage());
            return hardTrimToLimit(normalized, maxLength);
        }
    }

    // -------------------------------------------------------------------------
    // Patch Analysis
    // -------------------------------------------------------------------------

    private void completeWithoutPatch(SseEmitter emitter, Long questionId, String originalDraft,
                                      String washedKr, int maxLength, int minTarget, int preferredMax) throws Exception {
        WorkspaceQuestion question = questionRepository.findById(questionId).orElseThrow();
        question.setMistranslations("[]");

        String warningMessage = null;
        int finalLength = countVisibleChars(washedKr);
        boolean lengthOk = finalLength >= minTarget && (maxLength <= 0 || finalLength <= maxLength);
        DraftAnalysisResult.AiReviewReport reviewReport = null;
        if (finalLength < minTarget || (maxLength > 0 && finalLength > maxLength)) {
            warningMessage = String.format(
                    "세탁본이 %d자입니다. 목표 범위(%d-%d자)를 벗어나 길이 조건은 통과하지 못했습니다.",
                    finalLength, minTarget, maxLength);
            reviewReport = DraftAnalysisResult.AiReviewReport.builder()
                    .summary(warningMessage)
                    .build();
            log.warn("[!! 세탁본/길이초과] 최종={}자 | 목표={}~{}자", finalLength, minTarget, maxLength);
        } else {
            reviewReport = DraftAnalysisResult.AiReviewReport.builder()
                    .summary(String.format("글자 수 조건을 충족했습니다. 최종 세탁본은 %d자이며 목표 범위는 %d-%d자입니다.",
                            finalLength, minTarget, maxLength))
                    .build();
            log.info("[● 세탁본/길이통과] 최종={}자 | 목표={}~{}자", finalLength, minTarget, maxLength);
        }

        question.setAiReview(objectMapper.writeValueAsString(reviewReport));
        questionRepository.save(question);

        Map<String, Object> result = new HashMap<>();
        result.put("draft", washedKr);
        result.put("washedDraft", washedKr);
        result.put("sourceDraft", originalDraft);
        result.put("mistranslations", List.of());
        result.put("aiReviewReport", reviewReport);
        result.put("lengthOk", lengthOk);
        result.put("finalLength", finalLength);
        result.put("minTarget", minTarget);
        result.put("maxLength", maxLength);
        result.put("preferredMax", preferredMax);
        result.put("warningMessage", warningMessage);

        workspaceTaskCache.setComplete(questionId, result);
        sendStage(emitter, STAGE_DONE);
        sendSse(emitter, "complete", result);
        completeQuietly(emitter);
    }

    private void finalizePatch(SseEmitter emitter, Long questionId, String originalDraft,
                                String washedKr, int maxLength, int minTarget, int preferredMax,
                                DraftParams params) throws Exception {
        int findingTarget = Math.max(1, washedKr.length() / 300);
        DraftAnalysisResult analysis;
        try {
            analysis = workspacePatchAiService.analyzePatch(
                    originalDraft, washedKr,
                    maxLength, minTarget, findingTarget, "");
        } catch (Exception e) {
            log.warn("[!! 휴먼패치/분석실패] 처리=세탁본그대로사용 | 사유={}", e.getMessage());
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

        String warningMessage = null;
        int finalLength = countVisibleChars(responseDraft);
        if (finalLength < minTarget || (maxLength > 0 && finalLength > maxLength)) {
            warningMessage = String.format(
                    "세탁본은 %d자입니다. 번역 전 초안이 목표 범위를 통과했으므로 세탁본을 그대로 사용합니다.",
                    finalLength);
            log.warn("[!! 세탁본/길이초과] 최종={}자 | 목표={}~{}자", finalLength, minTarget, maxLength);
        } else {
            log.info("[● 세탁본/길이통과] 최종={}자 | 목표={}~{}자", finalLength, minTarget, maxLength);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("draft",       responseDraft);   // 프론트: 세탁본 표시용
        result.put("sourceDraft", originalDraft);   // 프론트: 원본 초안
        result.put("mistranslations", analysis.getMistranslations());
        result.put("aiReviewReport",  analysis.getAiReviewReport());
        result.put("minTarget",  minTarget);
        result.put("maxLength",  maxLength);
        result.put("warningMessage", warningMessage);

        workspaceTaskCache.setComplete(questionId, result);
        sendStage(emitter, STAGE_DONE);
        sendSse(emitter, "complete", result);
        completeQuietly(emitter);
    }

    private void completeWithWashFailed(SseEmitter emitter, Long questionId,
                                        String draft, int maxLength, int minTarget) throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("draft",          draft);
        result.put("washedDraft",    draft);
        result.put("sourceDraft",    draft);
        result.put("mistranslations", List.of());
        result.put("aiReviewReport", null);
        result.put("minTarget",      minTarget);
        result.put("maxLength",      maxLength);
        result.put("warningMessage", "번역 서비스에 일시적인 오류가 발생했습니다. AI 초안을 그대로 사용합니다. 번역 세탁은 '재번역' 버튼으로 다시 시도할 수 있습니다.");

        workspaceTaskCache.setComplete(questionId, result);
        sendStage(emitter, STAGE_DONE);
        sendSse(emitter, "complete", result);
        completeQuietly(emitter);
    }

    private void completeWithNoVerifiedExperience(SseEmitter emitter, Long questionId,
                                                  int maxLength, int minTarget, int preferredMax) throws Exception {
        String draft = "[" + NO_VERIFIED_EXPERIENCE_TITLE + "]\n" + NO_VERIFIED_EXPERIENCE_MESSAGE;
        Map<String, Object> result = new HashMap<>();
        result.put("draft", draft);
        result.put("washedDraft", draft);
        result.put("sourceDraft", draft);
        result.put("mistranslations", List.of());
        result.put("aiReviewReport", null);
        result.put("minTarget", minTarget);
        result.put("maxLength", maxLength);
        result.put("preferredMax", preferredMax);
        result.put("warningMessage", NO_VERIFIED_EXPERIENCE_MESSAGE);
        result.put("requiresExperienceSelection", true);

        workspaceTaskCache.setComplete(questionId, result);
        sendStage(emitter, STAGE_DONE);
        sendSse(emitter, "draft_intermediate", draft);
        sendSse(emitter, "complete", result);
        completeQuietly(emitter);
    }

    private void completeWithDraftLengthFailed(SseEmitter emitter, Long questionId,
                                               String draft, int maxLength, int minTarget,
                                               int preferredMax, String warningMessage) {
        Map<String, Object> result = new HashMap<>();
        result.put("draft",          draft);
        result.put("sourceDraft",    draft);
        result.put("mistranslations", List.of());
        result.put("aiReviewReport", null);
        result.put("minTarget",      minTarget);
        result.put("maxLength",      maxLength);
        result.put("preferredMax",   preferredMax);
        result.put("warningMessage", warningMessage);

        workspaceTaskCache.setComplete(questionId, result);
        sendStage(emitter, STAGE_DONE);
        sendSse(emitter, "complete", result);
        completeQuietly(emitter);
    }

    // -------------------------------------------------------------------------
    // Context Builders
    // -------------------------------------------------------------------------

    private boolean isNoVerifiedExperienceContext(String experienceContext) {
        return experienceContext != null && experienceContext.contains("NO_VERIFIED_EXPERIENCE_CONTEXT");
    }

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
                                          QuestionDraftPlan plan, List<Long> storyIds) {
        if (storyIds != null && !storyIds.isEmpty()) {
            return buildPersonalStoryContext(storyIds);
        }

        QuestionProfile profile = plan.toProfile();
        if (profile.primaryCategory() == QuestionCategory.PERSONAL_GROWTH) {
            List<Long> allStoryIds = personalStoryRepository.findAllByOrderByIdAsc().stream()
                    .map(PersonalStory::getId)
                    .toList();
            if (!allStoryIds.isEmpty()) {
                return buildPersonalStoryContext(allStoryIds);
            }
        }

        boolean reflectivePlan = isReflectiveNarrativePlan(plan);
        var results = experienceVectorRetrievalService.search(
                plan.experienceNeeds(), reflectivePlan ? 4 : 6, Set.of(), profile.primaryCategory());

        if (!results.isEmpty()) {
            logRagMatches(questionId, profile.primaryCategory(), results);
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

    private String buildExperienceContext(WorkspaceQuestion question, Long questionId,
                                           QuestionProfile profile, List<Long> storyIds) {
        if (storyIds != null && !storyIds.isEmpty()) {
            return buildPersonalStoryContext(storyIds);
        }

        if (profile.primaryCategory() == QuestionCategory.PERSONAL_GROWTH) {
            List<Long> allStoryIds = personalStoryRepository.findAllByOrderByIdAsc().stream()
                    .map(PersonalStory::getId)
                    .toList();
            if (!allStoryIds.isEmpty()) {
                return buildPersonalStoryContext(allStoryIds);
            }
        }

        // RAG: profile.ragKeywords를 supporting queries로 사용
        List<String> supportingQueries = new ArrayList<>(profile.ragKeywords());

        var results = experienceVectorRetrievalService.search(
                question.getTitle(), 4, Set.of(), supportingQueries,
                profile.primaryCategory());

        if (!results.isEmpty()) {
            logRagMatches(questionId, profile.primaryCategory(), results);
            return results.stream()
                    .map(item -> {
                        String header = (item.getFacetTitle() != null && !item.getFacetTitle().isBlank())
                                ? String.format("[%s | Facet: %s]", item.getExperienceTitle(), item.getFacetTitle())
                                : String.format("[%s]", item.getExperienceTitle());
                        return header + "\n" + item.getRelevantPart();
                    })
                    .collect(Collectors.joining("\n---\n"));
        }

        return NO_VERIFIED_EXPERIENCE_CONTEXT;
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
        log.info("[● RAG/매칭] 문항={} | 카테고리={} | 매칭={}개 | 사용후보={}",
                questionId, category, results.size(), matches);
    }

    private String buildCompactExperienceFallback() {
        List<Experience> experiences = experienceRepository.findAllWithFacets();
        if (experiences.isEmpty()) {
            return "No experience context available.";
        }

        return experiences.stream()
                .limit(2)
                .map(experience -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("[").append(firstNonBlank(experience.getTitle(), "Untitled Experience")).append("]\n");
                    if (experience.getDescription() != null && !experience.getDescription().isBlank()) {
                        sb.append("Summary: ").append(experience.getDescription()).append("\n");
                    }
                    if (experience.getRole() != null && !experience.getRole().isBlank()) {
                        sb.append("Role: ").append(experience.getRole()).append("\n");
                    }
                    if (experience.getFacets() != null && !experience.getFacets().isEmpty()) {
                        var facet = experience.getFacets().get(0);
                        sb.append("Representative facet: ").append(facet.getTitle()).append("\n");
                        appendFallbackFacetLine(sb, "Situation", facet.getSituation());
                        appendFallbackFacetLine(sb, "Action", facet.getActions());
                        appendFallbackFacetLine(sb, "Result", facet.getResults());
                    }
                    return sb.toString().trim();
                })
                .collect(Collectors.joining("\n---\n"));
    }

    private void appendFallbackFacetLine(StringBuilder sb, String label, String rawJsonArray) {
        String value = rawJsonArray == null ? "" : rawJsonArray
                .replaceAll("[\\[\\]\"]", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (!value.isBlank()) {
            sb.append(label).append(": ").append(value).append("\n");
        }
    }

    private String buildPersonalStoryContext(List<Long> storyIds) {
        List<PersonalStory> stories = personalStoryRepository.findAllById(storyIds).stream()
                .filter(story -> !PersonalStory.WRITING_GUIDE_LEGACY_TYPE.equals(story.getType()))
                .sorted(java.util.Comparator.comparing(PersonalStory::getId))
                .toList();
        if (stories.isEmpty()) {
            return "No personal life story context available.";
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
            sb.append(story.getContent()).append("\n");
            sb.append("---\n");
        }
        return sb.toString();
    }

    private String buildOthersContext(WorkspaceQuestion currentQuestion, Long questionId) {
        Long applicationId = resolveApplicationId(currentQuestion);
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

    private Long resolveApplicationId(WorkspaceQuestion question) {
        if (question == null || question.getApplication() == null) {
            return null;
        }
        return question.getApplication().getId();
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

    private String buildRefinementDirective(WorkspaceQuestion question, String userDirective,
                                            int maxLength, Integer targetChars) {
        List<String> parts = new ArrayList<>();
        if (question.getBatchStrategyDirective() != null && !question.getBatchStrategyDirective().isBlank()) {
            parts.add(question.getBatchStrategyDirective().trim());
        }
        if (userDirective != null && !userDirective.isBlank()) {
            parts.add(userDirective.trim());
        }
        if (targetChars != null) {
            parts.add(String.format("목표 글자 수: %d자", targetChars));
        }
        parts.add(String.format(
                "다듬기 기준: 현재 초안의 검증된 사실은 유지하되, 문항 의도와 사용자 지시를 우선하여 구조·근거·문체를 다시 정리하세요. 최대 글자 수는 %d자입니다.",
                maxLength));
        return String.join("\n", parts);
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

    private String toPlanJson(QuestionDraftPlan plan) {
        try {
            return objectMapper.writeValueAsString(plan);
        } catch (Exception e) {
            return String.valueOf(plan);
        }
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

    private LengthPolicy resolveLengthPolicy(int maxLength, Integer targetChars) {
        if (maxLength <= 0) {
            return new LengthPolicy(1, 1, 1, false);
        }

        if (targetChars != null && targetChars > 0) {
            int desired = (int) Math.round(Math.min(Math.max(1, targetChars), maxLength));
            int lower   = Math.max(1, (int) Math.ceil(desired * REQUESTED_TARGET_MIN_RATIO));
            return new LengthPolicy(Math.min(lower, desired), desired, maxLength, true);
        }

        int lower   = Math.max(1, (int) Math.ceil(maxLength * TARGET_MIN_RATIO));
        int desired = Math.max(lower, (int) Math.round(maxLength * DEFAULT_DESIRED_TARGET_RATIO));
        return new LengthPolicy(lower, desired, maxLength, false);
    }

    private String normalizeLengthText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private int countVisibleChars(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        String normalized = normalizeLengthText(text);
        int count = 0;
        for (int i = 0; i < normalized.length();) {
            int codePoint = normalized.codePointAt(i);
            i += Character.charCount(codePoint);

            if (codePoint == '\n') {
                count++;
                continue;
            }

            if (Character.isISOControl(codePoint)) {
                continue;
            }

            if (Character.getType(codePoint) == Character.FORMAT) {
                continue;
            }

            count++;
        }
        return count;
    }

    private String hardTrimToLimit(String text, int maxLength) {
        String normalized = normalizeLengthText(text).trim();
        if (maxLength <= 0 || countVisibleChars(normalized) <= maxLength) {
            return normalized;
        }

        int endIndex = normalized.offsetByCodePoints(0, maxLength);
        String trimmed = normalized.substring(0, endIndex).trim();
        int breakpoint = Math.max(
                Math.max(trimmed.lastIndexOf("\n"), trimmed.lastIndexOf(".")),
                Math.max(trimmed.lastIndexOf("!"), trimmed.lastIndexOf("?")));
        if (breakpoint >= Math.max(0, maxLength - 80)) {
            return trimmed.substring(0, breakpoint + 1).trim();
        }
        return trimmed;
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

    private record LengthPolicy(
            int minTarget,
            int desiredTarget,
            int hardLimit,
            boolean hasRequestedTarget
    ) {}

    private record WashedDraftCandidate(
            String sourceDraft,
            String washedDraft,
            int sourceLength,
            int washedLength,
            int score,
            int attempt,
            boolean lengthOk
    ) {
        private boolean isBetterThan(WashedDraftCandidate other) {
            if (other == null) {
                return true;
            }
            if (lengthOk != other.lengthOk) {
                return lengthOk;
            }
            if (score != other.score) {
                return score < other.score;
            }
            return attempt > other.attempt;
        }
    }

    private enum LengthDecision {
        ACCEPT,
        MINI_SHORTEN,
        REWRITE
    }

    private static class DraftLengthFitException extends RuntimeException {
        private final String bestDraft;

        DraftLengthFitException(String message, String bestDraft) {
            super(message);
            this.bestDraft = bestDraft;
        }

        String bestDraft() {
            return bestDraft;
        }
    }

    private static class SseConnectionClosedException extends RuntimeException {
        SseConnectionClosedException(Throwable cause) { super(cause); }
    }
}
