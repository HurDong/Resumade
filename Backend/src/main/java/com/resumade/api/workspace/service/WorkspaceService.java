package com.resumade.api.workspace.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.experience.domain.Experience;
import com.resumade.api.experience.domain.ExperienceRepository;
import com.resumade.api.experience.service.ExperienceVectorRetrievalService;
import com.resumade.api.workspace.domain.WorkspaceQuestion;
import com.resumade.api.workspace.domain.WorkspaceQuestionRepository;
import com.resumade.api.workspace.dto.DraftAnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceService {
    private static final long HEARTBEAT_INTERVAL_SECONDS = 8L;
    private static final Pattern REQUESTED_LENGTH_PATTERN = Pattern.compile(
            "([0-9][0-9,]{1,4})\\s*(?:\\uAE00\\uC790\\uC218|\\uAE00\\uC790|\\uC790|characters?|chars?)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PREFIX_MINIMUM_LENGTH_PATTERN = Pattern.compile(
            "(?:\\uCD5C\\uC18C|minimum|at\\s+least)\\s*([0-9][0-9,]{1,4})\\s*(?:\\uAE00\\uC790\\uC218|\\uAE00\\uC790|\\uC790|characters?|chars?)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SUFFIX_MINIMUM_LENGTH_PATTERN = Pattern.compile(
            "([0-9][0-9,]{1,4})\\s*(?:\\uAE00\\uC790\\uC218|\\uAE00\\uC790|\\uC790|characters?|chars?)\\s*(?:\\uC774\\uC0C1|\\uCD5C\\uC18C|or\\s+more|minimum)",
            Pattern.CASE_INSENSITIVE);
    private static final int MINIMUM_LENGTH_EXPANSION_ATTEMPTS = 3;
    private static final String LENGTH_RETRY_MARKER = "[LENGTH_RETRY]";
    private static final double DEFAULT_MIN_TARGET_FLOOR_RATIO = 0.85;
    private static final String NO_EXTRA_USER_DIRECTIVE = "No extra user directive.";
    private static final String STAGE_RAG = "RAG";
    private static final String STAGE_DRAFT = "DRAFT";
    private static final String STAGE_WASH = "WASH";
    private static final String STAGE_PATCH = "PATCH";
    private static final String STAGE_DONE = "DONE";

    private static class SseConnectionClosedException extends RuntimeException {
        private SseConnectionClosedException(Throwable cause) {
            super(cause);
        }
    }

    private final ExperienceRepository experienceRepository;
    private final WorkspaceDraftAiService workspaceDraftAiService;
    private final WorkspacePatchAiService workspacePatchAiService;
    private final TranslationService translationService;
    private final ObjectMapper objectMapper;
    private final WorkspaceQuestionRepository questionRepository;
    private final ExperienceVectorRetrievalService experienceVectorRetrievalService;

    public List<com.resumade.api.workspace.dto.ExperienceContextResponse.ContextItem> getMatchedExperiences(
            Long questionId, String customQuery) {
        WorkspaceQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found: " + questionId));

        String query = (customQuery != null && !customQuery.isBlank()) ? customQuery : question.getTitle();
        List<Experience> allExperiences = experienceRepository.findAll();
        return experienceVectorRetrievalService.search(query, 3,
                extractUsedExperienceIds(question, questionId, allExperiences));
    }

    @Transactional(readOnly = true)
    public void processRefinement(Long questionId, String directive, Integer targetChars, SseEmitter emitter) {
        HeartbeatHandle heartbeat = startHeartbeat(emitter);
        try {
            WorkspaceQuestion initialQuestion = questionRepository.findById(questionId)
                    .orElseThrow(() -> new RuntimeException("Question not found: " + questionId));

            String company = initialQuestion.getApplication().getCompanyName();
            String position = initialQuestion.getApplication().getPosition();
            String companyContext = buildApplicationResearchContext(initialQuestion);
            String questionTitle = initialQuestion.getTitle();
            String currentInput = initialQuestion.getWashedKr() != null
                    ? initialQuestion.getWashedKr()
                    : initialQuestion.getContent();

            sendProgress(emitter, STAGE_RAG, "지원하신 기업 정보와 문항을 토대로 초안 컨텍스트를 구성하고 있어요. 🏢");
            sendProgress(emitter, STAGE_RAG, "다른 문항과 겹치지 않도록 경험 데이터를 세밀하게 조정 중입니다. 🔍");

            List<Experience> allExperiences = experienceRepository.findAll();
            String others = buildOthersContext(initialQuestion, questionId, allExperiences);
            String context = buildFilteredContext(initialQuestion, questionId, allExperiences);

            paceProcessing();
            sendProgress(emitter, STAGE_DRAFT, "선택한 경험과 요청사항을 반영하여 초안을 다시 작성하고 있습니다. ✍️");

            int maxLength = initialQuestion.getMaxLength();
            int[] targetRange = resolveTargetRange(maxLength, directive, targetChars, 0.80, 0.95);
            int minTargetChars = targetRange[0];
            int maxTargetChars = targetRange[1];
            String directiveForPrompt = augmentDirectiveForPrompt(directive, maxLength, targetChars);

            WorkspaceDraftAiService.DraftResponse refineResponse = workspaceDraftAiService.refineDraft(
                    company,
                    position,
                    companyContext,
                    currentInput,
                    maxLength,
                    minTargetChars,
                    maxTargetChars,
                    context,
                    others,
                    directiveForPrompt);
            logLengthMetrics("refine", maxLength, minTargetChars, maxTargetChars, refineResponse.text, 0);

            String rawRefinedDraft = expandToMinimumLength(
                    normalizeTitleSpacing(refineResponse.text).trim(),
                    minTargetChars,
                    maxTargetChars,
                    maxLength,
                    company,
                    position,
                    questionTitle,
                    companyContext,
                    context,
                    others,
                    directiveForPrompt);
            String refinedDraft = prepareDraftForTranslation(
                    rawRefinedDraft,
                    maxLength,
                    company,
                    position,
                    companyContext,
                    context,
                    others);

            WorkspaceQuestion question = questionRepository.findById(questionId).orElseThrow();
            question.setContent(rawRefinedDraft);
            question.setUserDirective(directive);
            questionRepository.save(question);
            sendSse(emitter, "draft_intermediate", rawRefinedDraft);
            if (!refinedDraft.equals(rawRefinedDraft)) {
                sendSse(emitter, "washed_intermediate", refinedDraft);
            }

            paceProcessing();
            sendProgress(emitter, STAGE_WASH, "기계적인 말투를 지우기 위해 1차 가공(EN)을 진행 중입니다. 🚿");
            String translatedEn = translationService.translateToEnglish(refinedDraft);

            paceProcessing();
            sendProgress(emitter, STAGE_WASH, "자연스러운 한국어 문장으로 컴파일하여 세탁본을 완성 중입니다. 🧺");
            String washedKr = prepareDraftForTranslation(
                    normalizeTitleSpacing(translationService.translateToKorean(translatedEn)),
                    maxLength,
                    company,
                    position,
                    companyContext,
                    context,
                    others);
            washedKr = prepareDraftForTranslation(
                    expandToMinimumLength(
                            washedKr,
                            minTargetChars,
                            maxTargetChars,
                            maxLength,
                            company,
                            position,
                            questionTitle,
                            companyContext,
                            context,
                            others,
                            directiveForPrompt),
                    maxLength,
                    company,
                    position,
                    companyContext,
                    context,
                    others);

            question = questionRepository.findById(questionId).orElseThrow();
            question.setWashedKr(washedKr);
            questionRepository.save(question);
            sendSse(emitter, "washed_intermediate", washedKr);

            paceProcessing();
            sendProgress(emitter, STAGE_PATCH, "세탁된 문장에서 의미가 변하거나 어색한 부분을 '휴먼패치'로 점검 중입니다. 🩹");
            int maxLengthPatch = initialQuestion.getMaxLength();
            int findingTarget = calculateFindingTarget(washedKr);
            DraftAnalysisResult analysis = analyzePatchSafely(
                    emitter,
                    refinedDraft,
                    washedKr,
                    maxLengthPatch,
                    findingTarget,
                    context);

            paceProcessing();
            normalizeAnalysis(analysis, refinedDraft, washedKr, findingTarget);

            question = questionRepository.findById(questionId).orElseThrow();
            question.setMistranslations(objectMapper.writeValueAsString(analysis.getMistranslations()));
            question.setAiReview(objectMapper.writeValueAsString(analysis.getAiReviewReport()));

            String responseDraft = (analysis.getHumanPatchedText() != null && !analysis.getHumanPatchedText().isBlank())
                    ? analysis.getHumanPatchedText()
                    : washedKr;
            responseDraft = prepareDraftForTranslation(
                    normalizeTitleSpacing(responseDraft),
                    maxLength,
                    company,
                    position,
                    companyContext,
                    context,
                    others);

            Map<String, Object> result = new HashMap<>();
            result.put("draft", washedKr);
            result.put("humanPatched", responseDraft);
            result.put("mistranslations", analysis.getMistranslations());
            result.put("aiReviewReport", analysis.getAiReviewReport());
            logLengthMetrics("final", maxLength, minTargetChars, maxTargetChars, responseDraft, 0);

            sendStage(emitter, STAGE_DONE);
            sendSse(emitter, "complete", result);
            emitter.complete();
        } catch (SseConnectionClosedException e) {
            log.info("Refinement stream closed by client");
        } catch (Exception e) {
            log.error("Refinement process failed", e);
            try {
                sendSse(emitter, "error", resolveUserFacingErrorMessage(e));
            } catch (SseConnectionClosedException ignored) {
                log.info("Refinement stream already closed while reporting error");
            }
            completeQuietly(emitter);
        } finally {
            heartbeat.stop();
        }
    }

    @Transactional(readOnly = true)
    public void processHumanPatch(Long questionId, boolean useDirective, Integer targetChars, SseEmitter emitter) {
        HeartbeatHandle heartbeat = startHeartbeat(emitter);
        try {
            WorkspaceQuestion initialQuestion = questionRepository.findById(questionId)
                    .orElseThrow(() -> new RuntimeException("Question not found: " + questionId));

            String company = initialQuestion.getApplication().getCompanyName();
            String position = initialQuestion.getApplication().getPosition();
            String questionTitle = initialQuestion.getTitle();
            String companyContext = buildApplicationResearchContext(initialQuestion);

            sendComment(emitter, "flush buffer");

            sendProgress(emitter, STAGE_RAG, "자기소개서 작성을 위해 기업 분석 데이터와 문항을 준비하고 있어요. 📋");

            paceProcessing();

            sendProgress(emitter, STAGE_RAG, "경험이 중복되지 않도록 다른 문항의 작성 내용을 확인하고 있습니다. ✨");

            List<Experience> allExperiences = experienceRepository.findAll();
            String others = buildOthersContext(initialQuestion, questionId, allExperiences);

            sendProgress(emitter, STAGE_RAG, "문항에 가장 딱 맞는 나만의 핵심 경험을 선정하고 있어요. 🎯");

            String context = buildFilteredContext(initialQuestion, questionId, allExperiences);

            paceProcessing();
            sendProgress(emitter, STAGE_DRAFT, "엄선된 경험 데이터를 바탕으로 새로운 초안을 생성 중입니다. 🚀");

            int maxLengthGen = initialQuestion.getMaxLength();
            String rawDirective = initialQuestion.getUserDirective();
            String directiveForPrompt = useDirective
                    ? augmentDirectiveForPrompt(rawDirective, maxLengthGen, targetChars)
                    : NO_EXTRA_USER_DIRECTIVE;
            int[] targetRange = resolveTargetRange(maxLengthGen, rawDirective, targetChars, 0.80, 0.95);
            int minTargetChars = targetRange[0];
            int preferredTargetChars = targetRange[1];
            WorkspaceDraftAiService.DraftResponse draftResponse = workspaceDraftAiService.generateDraft(
                    company,
                    position,
                    questionTitle,
                    companyContext,
                    maxLengthGen,
                    minTargetChars,
                    preferredTargetChars,
                    context,
                    others,
                    directiveForPrompt);
            logLengthMetrics("generate", maxLengthGen, minTargetChars, preferredTargetChars, draftResponse.text, 0);

            String rawDraft = expandToMinimumLength(
                    normalizeTitleSpacing(draftResponse.text).trim(),
                    minTargetChars,
                    preferredTargetChars,
                    maxLengthGen,
                    company,
                    position,
                    questionTitle,
                    companyContext,
                    context,
                    others,
                    directiveForPrompt);
            String draft = prepareDraftForTranslation(
                    rawDraft,
                    maxLengthGen,
                    company,
                    position,
                    companyContext,
                    context,
                    others);

            WorkspaceQuestion question = questionRepository.findById(questionId).orElseThrow();
            question.setContent(rawDraft);
            questionRepository.save(question);
            sendSse(emitter, "draft_intermediate", rawDraft);
            if (!draft.equals(rawDraft)) {
                sendSse(emitter, "washed_intermediate", draft);
            }

            paceProcessing();
            sendProgress(emitter, STAGE_WASH, "기계적인 말투를 제거하기 위해 1차 번역 공정을 진행하고 있습니다. 🚿");
            String translatedEn = translationService.translateToEnglish(draft);

            paceProcessing();
            sendProgress(emitter, STAGE_WASH, "자연스러운 한국어 문장으로 컴파일하여 세탁본을 완성 중입니다. 🧺");
            String washedKr = prepareDraftForTranslation(
                    normalizeTitleSpacing(translationService.translateToKorean(translatedEn)),
                    maxLengthGen,
                    company,
                    position,
                    companyContext,
                    context,
                    others);
            washedKr = prepareDraftForTranslation(
                    expandToMinimumLength(
                            washedKr,
                            minTargetChars,
                            preferredTargetChars,
                            maxLengthGen,
                            company,
                            position,
                            questionTitle,
                            companyContext,
                            context,
                            others,
                            directiveForPrompt),
                    maxLengthGen,
                    company,
                    position,
                    companyContext,
                    context,
                    others);

            question = questionRepository.findById(questionId).orElseThrow();
            question.setWashedKr(washedKr);
            questionRepository.save(question);
            sendSse(emitter, "washed_intermediate", washedKr);

            paceProcessing();
            sendProgress(emitter, STAGE_PATCH, "더욱 사람 냄새 나는 글을 위해 '휴먼패치' 분석을 진행하고 있어요. 👤");
            int maxLengthFinal = initialQuestion.getMaxLength();
            int findingTarget = calculateFindingTarget(washedKr);
            DraftAnalysisResult analysis = analyzePatchSafely(
                    emitter,
                    draft,
                    washedKr,
                    maxLengthFinal,
                    findingTarget,
                    context);

            paceProcessing();
            normalizeAnalysis(analysis, draft, washedKr, findingTarget);

            question = questionRepository.findById(questionId).orElseThrow();
            question.setMistranslations(objectMapper.writeValueAsString(analysis.getMistranslations()));
            question.setAiReview(objectMapper.writeValueAsString(analysis.getAiReviewReport()));

            String responseDraft = (analysis.getHumanPatchedText() != null && !analysis.getHumanPatchedText().isBlank())
                    ? analysis.getHumanPatchedText()
                    : washedKr;
            responseDraft = prepareDraftForTranslation(
                    normalizeTitleSpacing(responseDraft),
                    maxLengthFinal,
                    company,
                    position,
                    companyContext,
                    context,
                    others);

            Map<String, Object> result = new HashMap<>();
            result.put("draft", washedKr);
            result.put("humanPatched", responseDraft);
            result.put("mistranslations", analysis.getMistranslations());
            result.put("aiReviewReport", analysis.getAiReviewReport());
            logLengthMetrics("final", maxLengthFinal, minTargetChars, preferredTargetChars, responseDraft, 0);

            sendStage(emitter, STAGE_DONE);
            sendSse(emitter, "complete", result);
            emitter.complete();
        } catch (SseConnectionClosedException e) {
            log.info("Human patch stream closed by client");
        } catch (Exception e) {
            log.error("Human Patch process failed", e);
            try {
                sendSse(emitter, "error", resolveUserFacingErrorMessage(e));
            } catch (SseConnectionClosedException ignored) {
                log.info("Human patch stream already closed while reporting error");
            }
            completeQuietly(emitter);
        } finally {
            heartbeat.stop();
        }
    }

    @Transactional(readOnly = true)
    public void processRewash(Long questionId, SseEmitter emitter) {
        HeartbeatHandle heartbeat = startHeartbeat(emitter);
        try {
            WorkspaceQuestion question = questionRepository.findById(questionId)
                    .orElseThrow(() -> new RuntimeException("Question not found: " + questionId));

            String draft = question.getContent();
            if (draft == null || draft.isBlank()) {
                throw new IllegalStateException("Draft is empty");
            }

            String company = question.getApplication().getCompanyName();
            String position = question.getApplication().getPosition();
            String questionTitle = question.getTitle();
            String companyContext = buildApplicationResearchContext(question);
            List<Experience> allExperiences = experienceRepository.findAll();
            String others = buildOthersContext(question, questionId, allExperiences);
            String context = buildFilteredContext(question, questionId, allExperiences);

            sendComment(emitter, "flush buffer");
            sendProgress(emitter, STAGE_DRAFT, "현재 초안을 바탕으로 세탁 파이프라인을 다시 시작합니다. 🔄");
            sendSse(emitter, "draft_intermediate", draft);

            paceProcessing();
            sendProgress(emitter, STAGE_WASH, "초안 고도화를 위해 중간 번역 과정을 거치고 있습니다. 🚿");
            String translatedEn = translationService.translateToEnglish(draft);

            paceProcessing();
            sendProgress(emitter, STAGE_WASH, "한국어로 다시 번역하며 글자 수를 최적화하고 있습니다. 📏");

            int maxLength = question.getMaxLength();
            String rawDirective = question.getUserDirective();
            String directiveForPrompt = augmentDirectiveForPrompt(rawDirective, maxLength, null);
            int[] targetRange = resolveTargetRange(maxLength, rawDirective, null, 0.80, 0.95);
            int minTargetChars = targetRange[0];
            int preferredTargetChars = targetRange[1];

            String washedKr = prepareDraftForTranslation(
                    normalizeTitleSpacing(translationService.translateToKorean(translatedEn)),
                    maxLength,
                    company,
                    position,
                    companyContext,
                    context,
                    others);
            washedKr = prepareDraftForTranslation(
                    expandToMinimumLength(
                            washedKr,
                            minTargetChars,
                            preferredTargetChars,
                            maxLength,
                            company,
                            position,
                            questionTitle,
                            companyContext,
                            context,
                            others,
                            directiveForPrompt),
                    maxLength,
                    company,
                    position,
                    companyContext,
                    context,
                    others);

            question = questionRepository.findById(questionId).orElseThrow();
            question.setWashedKr(washedKr);
            questionRepository.save(question);
            sendSse(emitter, "washed_intermediate", washedKr);

            paceProcessing();
            sendProgress(emitter, STAGE_PATCH, "정제된 문장을 바탕으로 휴먼패치 분석을 다시 실행합니다. 💫");
            finalizePatchAnalysis(
                    emitter,
                    questionId,
                    draft,
                    washedKr,
                    maxLength,
                    context,
                    company,
                    position,
                    companyContext,
                    others);
            emitter.complete();
        } catch (SseConnectionClosedException e) {
            log.info("Rewash stream closed by client");
        } catch (Exception e) {
            log.error("Rewash process failed", e);
            try {
                sendSse(emitter, "error", resolveUserFacingErrorMessage(e));
            } catch (SseConnectionClosedException ignored) {
                log.info("Rewash stream already closed while reporting error");
            }
            completeQuietly(emitter);
        } finally {
            heartbeat.stop();
        }
    }

    @Transactional(readOnly = true)
    public void processRepatch(Long questionId, SseEmitter emitter) {
        HeartbeatHandle heartbeat = startHeartbeat(emitter);
        try {
            WorkspaceQuestion question = questionRepository.findById(questionId)
                    .orElseThrow(() -> new RuntimeException("Question not found: " + questionId));

            String draft = question.getContent();
            String washedKr = question.getWashedKr();
            if (draft == null || draft.isBlank()) {
                throw new IllegalStateException("Draft is empty");
            }
            if (washedKr == null || washedKr.isBlank()) {
                throw new IllegalStateException("Washed draft is empty");
            }

            List<Experience> allExperiences = experienceRepository.findAll();
            String context = buildFilteredContext(question, questionId, allExperiences);
            String company = question.getApplication().getCompanyName();
            String position = question.getApplication().getPosition();
            String companyContext = buildApplicationResearchContext(question);
            String others = buildOthersContext(question, questionId, allExperiences);

            sendComment(emitter, "flush buffer");
            sendProgress(emitter, STAGE_PATCH, "재분석을 위해 초안과 세탁본을 불러오고 있습니다. 📂");
            sendSse(emitter, "draft_intermediate", draft);
            sendSse(emitter, "washed_intermediate", washedKr);

            paceProcessing();
            sendProgress(emitter, STAGE_PATCH, "더 완벽한 문장을 위해 핵심 패치 포인트를 다시 분석합니다. 🔍");
            finalizePatchAnalysis(
                    emitter,
                    questionId,
                    draft,
                    washedKr,
                    question.getMaxLength(),
                    context,
                    company,
                    position,
                    companyContext,
                    others);
            emitter.complete();
        } catch (SseConnectionClosedException e) {
            log.info("Repatch stream closed by client");
        } catch (Exception e) {
            log.error("Repatch process failed", e);
            try {
                sendSse(emitter, "error", resolveUserFacingErrorMessage(e));
            } catch (SseConnectionClosedException ignored) {
                log.info("Repatch stream already closed while reporting error");
            }
            completeQuietly(emitter);
        } finally {
            heartbeat.stop();
        }
    }

    private void finalizePatchAnalysis(
            SseEmitter emitter,
            Long questionId,
            String originalDraft,
            String washedKr,
            int maxLength,
            String context,
            String company,
            String position,
            String companyContext,
            String others) throws Exception {
        int findingTarget = calculateFindingTarget(washedKr);
        DraftAnalysisResult analysis = analyzePatchSafely(
                emitter,
                originalDraft,
                washedKr,
                maxLength,
                findingTarget,
                context);

        paceProcessing();
        normalizeAnalysis(analysis, originalDraft, washedKr, findingTarget);

        WorkspaceQuestion question = questionRepository.findById(questionId).orElseThrow();
        question.setMistranslations(objectMapper.writeValueAsString(analysis.getMistranslations()));
        question.setAiReview(objectMapper.writeValueAsString(analysis.getAiReviewReport()));
        question.setWashedKr(washedKr);
        questionRepository.save(question);

        String responseDraft = (analysis.getHumanPatchedText() != null && !analysis.getHumanPatchedText().isBlank())
                ? analysis.getHumanPatchedText()
                : washedKr;
        responseDraft = prepareDraftForTranslation(
                normalizeTitleSpacing(responseDraft),
                maxLength,
                company,
                position,
                companyContext,
                context,
                others);

        Map<String, Object> result = new HashMap<>();
        result.put("draft", washedKr);
        result.put("humanPatched", responseDraft);
        result.put("mistranslations", analysis.getMistranslations());
        result.put("aiReviewReport", analysis.getAiReviewReport());
        int[] finalTargetRange = resolveTargetRange(maxLength, null, null, 0.80, 0.95);
        logLengthMetrics("final", maxLength, finalTargetRange[0], finalTargetRange[1], responseDraft, 0);

        sendStage(emitter, STAGE_DONE);
        sendSse(emitter, "complete", result);
    }

    private HeartbeatHandle startHeartbeat(SseEmitter emitter) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException | IllegalStateException e) {
                log.debug("Stopping workspace heartbeat: {}", e.getMessage());
                throw new SseConnectionClosedException(e);
            }
        }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);

        emitter.onCompletion(() -> future.cancel(true));
        emitter.onTimeout(() -> future.cancel(true));
        emitter.onError(error -> future.cancel(true));

        return new HeartbeatHandle(scheduler, future);
    }

    private String buildOthersContext(WorkspaceQuestion initialQuestion, Long questionId,
            List<Experience> allExperiences) {
        return initialQuestion.getApplication().getQuestions().stream()
                .filter(q -> !q.getId().equals(questionId))
                .map(q -> {
                    String content = q.getContent() != null ? q.getContent() : "";
                    String usedProject = allExperiences.stream()
                            .filter(exp -> content.contains(exp.getTitle()))
                            .map(Experience::getTitle)
                            .findFirst()
                            .orElse("None specified");
                    return String.format("[Other question: %s | Current content: %s | Used experience: %s]",
                            q.getTitle(), content, usedProject);
                })
                .collect(Collectors.joining("\n"));
    }

    private String resolveUserFacingErrorMessage(Exception e) {
        if (e instanceof IllegalStateException && e.getMessage() != null
                && e.getMessage().contains("minimum length requirement")) {
            return "최소 글자 수 요구사항을 충족하지 못했습니다. 요청사항을 완화하거나 최대 글자 수를 늘려보세요. ⚠️";
        }
        return "초안 작성 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요. 🛠️";
    }

    private int[] resolveTargetRange(
            int maxLength,
            String directive,
            Integer targetChars,
            double defaultMinRatio,
            double defaultMaxRatio) {
        if (targetChars != null && targetChars > 0) {
            int preferredTarget = targetChars;
            if (maxLength > 0) {
                preferredTarget = Math.min(preferredTarget, maxLength);
            }
            preferredTarget = Math.max(1, preferredTarget);
            int minTarget = Math.max(1, (int) Math.round(preferredTarget * 0.90));
            return new int[] { minTarget, preferredTarget };
        }

        RequestedLengthDirective requestedLength = extractRequestedLengthDirective(directive, maxLength);
        if (requestedLength != null) {
            return new int[] { requestedLength.minimum(), requestedLength.preferredTarget() };
        }

        if (maxLength <= 0) {
            return new int[] { 1, 1 };
        }

        double effectiveMinRatio = Math.max(defaultMinRatio, DEFAULT_MIN_TARGET_FLOOR_RATIO);
        int preferredTarget = Math.max(1, (int) Math.round(maxLength * defaultMaxRatio));
        preferredTarget = Math.min(preferredTarget, maxLength);

        int minTarget = Math.max(1, (int) Math.round(maxLength * effectiveMinRatio));
        minTarget = Math.min(minTarget, preferredTarget);
        return new int[] { minTarget, preferredTarget };
    }

    private String augmentDirectiveForPrompt(String directive, int maxLength, Integer targetChars) {
        String normalized = directive == null || directive.isBlank() ? NO_EXTRA_USER_DIRECTIVE : directive.trim();
        RequestedLengthDirective requestedLength;
        if (targetChars != null && targetChars > 0) {
            int preferred = targetChars;
            if (maxLength > 0) {
                preferred = Math.min(preferred, maxLength);
            }
            preferred = Math.max(1, preferred);
            int min = Math.max(1, (int) Math.round(preferred * 0.90));
            requestedLength = new RequestedLengthDirective(min, preferred);
        } else {
            requestedLength = extractRequestedLengthDirective(directive, maxLength);
        }
        if (requestedLength == null) {
            return normalized;
        }

        StringBuilder builder = new StringBuilder();
        if (!NO_EXTRA_USER_DIRECTIVE.equals(normalized)) {
            builder.append(normalized).append("\n");
        }

        builder.append("Length guidance: minimum required length is ")
                .append(requestedLength.minimum())
                .append(" characters. Count visible characters including spaces and line breaks, and treat each visible character as 1.");
        builder.append(" Preferred target is ")
                .append(requestedLength.preferredTarget())
                .append(" characters.");

        if (requestedLength.preferredTarget() > maxLength) {
            builder.append(" Current hard limit is ")
                    .append(maxLength)
                    .append(" characters, so stay within that limit while getting as close as possible.");
        }

        builder.append(
                " Before returning, recount characters and, if the answer is too short, add concrete evidence and explanation before recounting.");

        return builder.toString();
    }

    private RequestedLengthDirective extractRequestedLengthDirective(String directive, int maxLength) {
        if (directive == null || directive.isBlank()) {
            return null;
        }

        List<Integer> mentionedLengths = new ArrayList<>();
        Matcher matcher = REQUESTED_LENGTH_PATTERN.matcher(directive);
        while (matcher.find()) {
            mentionedLengths.add(parseLengthNumber(matcher.group(1)));
        }
        if (mentionedLengths.isEmpty()) {
            return null;
        }

        Integer explicitMinimum = extractPatternLength(directive, PREFIX_MINIMUM_LENGTH_PATTERN,
                SUFFIX_MINIMUM_LENGTH_PATTERN);
        int minimum = explicitMinimum != null ? explicitMinimum : mentionedLengths.get(0);
        int preferredTarget = resolvePreferredRequestedTarget(mentionedLengths, minimum);

        if (maxLength > 0) {
            minimum = Math.min(minimum, maxLength);
            preferredTarget = Math.min(preferredTarget, maxLength);
        }

        preferredTarget = Math.max(minimum, preferredTarget);
        return new RequestedLengthDirective(Math.max(1, minimum), Math.max(1, preferredTarget));
    }

    private Integer extractPatternLength(String directive, Pattern... patterns) {
        if (directive == null || directive.isBlank()) {
            return null;
        }

        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(directive);
            if (matcher.find()) {
                return parseLengthNumber(matcher.group(1));
            }
        }

        return null;
    }

    private int parseLengthNumber(String value) {
        return Integer.parseInt(value.replace(",", ""));
    }

    private int resolvePreferredRequestedTarget(List<Integer> mentionedLengths, int minimum) {
        if (mentionedLengths == null || mentionedLengths.isEmpty()) {
            return minimum;
        }

        if (mentionedLengths.size() == 1) {
            return mentionedLengths.get(0);
        }

        int ceiling = mentionedLengths.stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(minimum);

        if (ceiling <= minimum) {
            return minimum;
        }

        return minimum + (int) Math.round((ceiling - minimum) * 0.8);
    }

    private String expandToMinimumLength(
            String text,
            int minTargetChars,
            int preferredTargetChars,
            int maxLength,
            String company,
            String position,
            String questionTitle,
            String companyContext,
            String context,
            String others,
            String directive) {
        String normalized = normalizeLengthText(normalizeTitleSpacing(text)).trim();
        int preferredTarget = resolvePreferredTargetForExpansion(minTargetChars, preferredTargetChars, maxLength);
        int currentLength = countResumeCharacters(normalized);
        logLengthMetrics("expand", maxLength, minTargetChars, preferredTarget, normalized, 0);
        if (normalized.isBlank() || minTargetChars <= 0) {
            return normalized;
        }
        if (currentLength >= preferredTarget) {
            return normalized;
        }

        String candidate = normalized;

        for (int attempt = 1; attempt <= MINIMUM_LENGTH_EXPANSION_ATTEMPTS; attempt++) {
            int candidateLength = countResumeCharacters(candidate);
            if (candidateLength >= preferredTarget) {
                return candidate;
            }

            String expansionDirective = buildMinimumLengthDirective(
                    directive,
                    candidateLength,
                    minTargetChars,
                    preferredTarget,
                    maxLength,
                    attempt);

            log.warn("Draft under target. current={}, min={}, preferred={}, hardLimit={}, attempt={}",
                    candidateLength, minTargetChars, preferredTarget, maxLength, attempt);
            try {
                WorkspaceDraftAiService.DraftResponse expanded = workspaceDraftAiService.refineDraft(
                        company,
                        position,
                        companyContext,
                        candidate,
                        maxLength,
                        minTargetChars,
                        preferredTarget,
                        context,
                        others,
                        expansionDirective);

                if (expanded == null || expanded.text == null || expanded.text.isBlank()) {
                    continue;
                }

                String expandedCandidate = prepareDraftForTranslation(
                        expanded.text,
                        maxLength,
                        company,
                        position,
                        companyContext,
                        context,
                        others);
                if (countResumeCharacters(expandedCandidate) > countResumeCharacters(candidate)) {
                    candidate = expandedCandidate;
                }
                logLengthMetrics("expand", maxLength, minTargetChars, preferredTarget, candidate, attempt);

                if (countResumeCharacters(candidate) >= preferredTarget) {
                    return candidate;
                }
            } catch (Exception e) {
                log.warn(
                        "Failed to expand under-length draft. current={}, min={}, preferred={}, hardLimit={}, attempt={}",
                        countResumeCharacters(candidate), minTargetChars, preferredTarget, maxLength, attempt, e);
            }
        }

        int finalLength = countResumeCharacters(candidate);
        if (finalLength < minTargetChars) {
            log.warn("expandToMinimumLength: gave up after {} attempts. current={}, min={}, preferred={}, hardLimit={}",
                    MINIMUM_LENGTH_EXPANSION_ATTEMPTS, finalLength, minTargetChars, preferredTarget, maxLength);
        }

        return candidate;
    }

    private String buildMinimumLengthDirective(
            String directive,
            int previousLength,
            int minTargetChars,
            int preferredTargetChars,
            int maxLength,
            int attempt) {
        StringBuilder builder = new StringBuilder();
        builder.append(LENGTH_RETRY_MARKER).append("\n");
        if (directive != null && !directive.isBlank()) {
            builder.append(directive.trim()).append("\n");
        }
        builder.append("Retry feedback: previous output length was ")
                .append(previousLength)
                .append(" characters, which is below the minimum target.\n");
        builder.append("Retry attempt: ")
                .append(attempt)
                .append(" / ")
                .append(MINIMUM_LENGTH_EXPANSION_ATTEMPTS)
                .append(".\n");
        builder.append("Target range for this retry: ")
                .append(minTargetChars)
                .append(" to ")
                .append(preferredTargetChars)
                .append(" characters.\n");
        builder.append("Hard limit: ")
                .append(maxLength)
                .append(" characters.\n");
        builder.append("Preserve all strong facts from the current draft.\n");
        builder.append("Expand only missing depth. Do not summarize, compress, or delete existing strong evidence.\n");
        builder.append(
                "Expansion order: background -> role -> judgment -> execution detail -> measurable result -> job connection.\n");
        builder.append("Count spaces and line breaks as 1 character each. Generic filler is forbidden.");
        return builder.toString();
    }

    private int resolvePreferredTargetForExpansion(int minTargetChars, int preferredTargetChars, int maxLength) {
        int preferred = Math.max(minTargetChars, preferredTargetChars);
        if (maxLength > 0) {
            preferred = Math.min(preferred, maxLength);
        }
        return Math.max(1, preferred);
    }

    private String buildApplicationResearchContext(WorkspaceQuestion question) {
        List<String> sections = new ArrayList<>();

        if (question.getApplication().getCompanyResearch() != null
                && !question.getApplication().getCompanyResearch().isBlank()) {
            sections.add("[Company Research]\n" + question.getApplication().getCompanyResearch());
        }

        if (question.getApplication().getAiInsight() != null && !question.getApplication().getAiInsight().isBlank()) {
            sections.add("[JD Insight]\n" + question.getApplication().getAiInsight());
        }

        if (question.getApplication().getRawJd() != null && !question.getApplication().getRawJd().isBlank()) {
            sections.add("[Raw JD]\n" + question.getApplication().getRawJd());
        }

        if (sections.isEmpty()) {
            return "No company-specific research context available.";
        }

        return String.join("\n---\n", sections);
    }

    private String buildFilteredContext(WorkspaceQuestion initialQuestion, Long questionId,
            List<Experience> allExperiences) {
        Set<Long> excludedExperienceIds = extractUsedExperienceIds(initialQuestion, questionId, allExperiences);
        List<com.resumade.api.workspace.dto.ExperienceContextResponse.ContextItem> selectedContext = experienceVectorRetrievalService
                .search(initialQuestion.getTitle(), 4, excludedExperienceIds);

        if (!selectedContext.isEmpty()) {
            return selectedContext.stream()
                    .map(item -> String.format("[Matched Experience: %s]\n%s", item.getExperienceTitle(),
                            item.getRelevantPart()))
                    .collect(Collectors.joining("\n---\n"));
        }

        List<Experience> filteredExperiences = allExperiences.stream()
                .filter(exp -> !excludedExperienceIds.contains(exp.getId()))
                .toList();

        if (filteredExperiences.isEmpty()) {
            filteredExperiences = allExperiences;
        }

        return filteredExperiences.stream()
                .map(Experience::getRawContent)
                .collect(Collectors.joining("\n---\n"));
    }

    private Set<Long> extractUsedExperienceIds(WorkspaceQuestion initialQuestion, Long questionId,
            List<Experience> allExperiences) {
        return initialQuestion.getApplication().getQuestions().stream()
                .filter(q -> !q.getId().equals(questionId))
                .map(q -> {
                    String searchableContent = String.join("\n",
                            q.getContent() == null ? "" : q.getContent(),
                            q.getWashedKr() == null ? "" : q.getWashedKr());
                    return allExperiences.stream()
                            .filter(exp -> searchableContent.contains(exp.getTitle()))
                            .map(Experience::getId)
                            .findFirst()
                            .orElse(null);
                })
                .filter(id -> id != null)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalizeTitleSpacing(String text) {
        if (text == null) {
            return null;
        }

        String normalized = text.replaceAll("\\*\\*\\[", "[")
                .replaceAll("\\]\\*\\*", "]");

        if (normalized.contains("]") && !normalized.contains("]\n\n")) {
            normalized = normalized.replaceFirst("\\]\n", "]\n\n");
        }

        return normalized;
    }

    private String enforceLengthLimit(
            String text,
            int maxLength,
            String company,
            String position,
            String companyContext,
            String context,
            String others) {
        if (text == null) {
            return null;
        }

        String normalized = normalizeLengthText(normalizeTitleSpacing(text)).trim();
        if (maxLength <= 0 || countResumeCharacters(normalized) <= maxLength) {
            return normalized;
        }

        int[] defaultRange = resolveTargetRange(maxLength, null, null, 0.80, 0.95);
        logLengthMetrics("shorten", maxLength, defaultRange[0], defaultRange[1], normalized, 0);
        log.warn("Draft exceeded max length. current={}, limit={}", countResumeCharacters(normalized), maxLength);

        String safeCompany = safeTrim(company);
        String safePosition = safeTrim(position);
        String safeCompanyContext = safeTrim(companyContext);
        String safeContext = safeTrim(context);
        String safeOthers = safeTrim(others);

        try {
            WorkspaceDraftAiService.DraftResponse shortened = workspaceDraftAiService.shortenToLimit(
                    safeCompany,
                    safePosition,
                    safeCompanyContext,
                    normalized,
                    maxLength,
                    safeContext,
                    safeOthers);

            if (shortened == null || shortened.text == null || shortened.text.isBlank()) {
                log.warn("Length enforcement AI retry returned empty text. Falling back to hard trim.");
                String trimmed = hardTrimToLimit(normalized, maxLength);
                logLengthMetrics("shorten", maxLength, defaultRange[0], defaultRange[1], trimmed, 2);
                return trimmed;
            }

            String candidate = normalizeLengthText(normalizeTitleSpacing(shortened.text)).trim();
            if (countResumeCharacters(candidate) <= maxLength) {
                logLengthMetrics("shorten", maxLength, defaultRange[0], defaultRange[1], candidate, 1);
                return candidate;
            }

            log.warn("Length enforcement retry still exceeded limit. current={}, limit={}",
                    countResumeCharacters(candidate), maxLength);
            String trimmed = hardTrimToLimit(candidate, maxLength);
            logLengthMetrics("shorten", maxLength, defaultRange[0], defaultRange[1], trimmed, 2);
            return trimmed;
        } catch (Exception e) {
            log.warn("Length enforcement AI retry failed. Falling back to hard trim.", e);
            String trimmed = hardTrimToLimit(normalized, maxLength);
            logLengthMetrics("shorten", maxLength, defaultRange[0], defaultRange[1], trimmed, 2);
            return trimmed;
        }
    }

    private String hardTrimToLimit(String text, int maxLength) {
        if (text == null || maxLength <= 0) {
            return text;
        }

        String normalized = normalizeLengthText(text);
        if (countResumeCharacters(normalized) <= maxLength) {
            return normalized;
        }

        String trimmed = substringByCharacterLimit(normalized, maxLength).trim();
        int breakpoint = Math.max(
                Math.max(trimmed.lastIndexOf("\n"), trimmed.lastIndexOf(".")),
                Math.max(trimmed.lastIndexOf("!"), trimmed.lastIndexOf("?")));

        if (breakpoint >= Math.max(0, maxLength - 80)) {
            return trimmed.substring(0, breakpoint + 1).trim();
        }

        int softBreakpoint = Math.max(trimmed.lastIndexOf(","), trimmed.lastIndexOf(" "));
        if (softBreakpoint >= Math.max(0, maxLength - 40)) {
            return appendEllipsisWithinLimit(trimmed.substring(0, softBreakpoint).trim(), maxLength);
        }

        return appendEllipsisWithinLimit(trimmed, maxLength);
    }

    private String appendEllipsisWithinLimit(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return text;
        }
        if (text.endsWith(".") || text.endsWith("!") || text.endsWith("?")) {
            return text;
        }

        String base = text;
        if (maxLength > 3 && countResumeCharacters(base) > maxLength - 3) {
            base = substringByCharacterLimit(base, maxLength - 3).trim();
        }

        if (base.isBlank()) {
            return text;
        }

        return base + "...";
    }

    private String prepareDraftForTranslation(
            String text,
            int maxLength,
            String company,
            String position,
            String companyContext,
            String context,
            String others) {
        if (text == null) {
            return null;
        }

        String normalized = normalizeLengthText(normalizeTitleSpacing(text)).trim();
        if (maxLength > 0 && countResumeCharacters(normalized) > maxLength) {
            return enforceLengthLimit(normalized, maxLength, company, position, companyContext, context, others);
        }

        return normalized;
    }

    private String prepareDraftForTranslation(String text, int maxLength) {
        if (text == null) {
            return null;
        }

        String normalized = normalizeLengthText(normalizeTitleSpacing(text)).trim();
        if (maxLength > 0 && countResumeCharacters(normalized) > maxLength) {
            int[] defaultRange = resolveTargetRange(maxLength, null, null, 0.80, 0.95);
            logLengthMetrics("shorten", maxLength, defaultRange[0], defaultRange[1], normalized, 0);
            log.warn("Draft exceeded max length. current={}, limit={}", countResumeCharacters(normalized), maxLength);
            String trimmed = hardTrimToLimit(normalized, maxLength);
            logLengthMetrics("shorten", maxLength, defaultRange[0], defaultRange[1], trimmed, 1);
            return trimmed;
        }

        return normalized;
    }

    private String normalizeLengthText(String text) {
        if (text == null) {
            return null;
        }
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private int countResumeCharacters(String text) {
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

    private void logLengthMetrics(
            String stage,
            int hardLimit,
            int minimumTarget,
            int preferredTarget,
            String text,
            int retryCount) {
        int actualChars = countResumeCharacters(text);
        boolean underMin = minimumTarget > 0 && actualChars < minimumTarget;
        boolean overHard = hardLimit > 0 && actualChars > hardLimit;
        log.info(
                "LengthMetrics stage={} hardLimit={} minimumTarget={} preferredTarget={} actualChars={} underMin={} overHard={} retry={}",
                stage, hardLimit, minimumTarget, preferredTarget, actualChars, underMin, overHard, retryCount);
    }

    private String substringByCharacterLimit(String text, int maxLength) {
        if (text == null) {
            return null;
        }

        String normalized = normalizeLengthText(text);
        if (maxLength <= 0 || countResumeCharacters(normalized) <= maxLength) {
            return normalized;
        }

        int endIndex = normalized.offsetByCodePoints(0, maxLength);
        return normalized.substring(0, endIndex);
    }

    private DraftAnalysisResult analyzePatchSafely(
            SseEmitter emitter,
            String originalDraft,
            String washedKr,
            int maxLength,
            int findingTarget,
            String context) {
        try {
            return workspacePatchAiService.analyzePatch(
                    sanitizeOpenAiPromptText(originalDraft),
                    sanitizeOpenAiPromptText(washedKr),
                    maxLength,
                    (int) (maxLength * 0.92),
                    findingTarget,
                    sanitizeOpenAiPromptText(context));
        } catch (Exception e) {
            if (isQuotaError(e) || isPatchAnalysisRequestError(e) || isTimeoutError(e)) {
                log.warn("Patch analysis skipped due to upstream OpenAI issue", e);
                sendProgress(emitter, STAGE_PATCH, "휴먼패치 분석 결과가 불안정하여 세탁본만 반환합니다. ⚠️");

                return DraftAnalysisResult.builder()
                        .humanPatchedText(washedKr)
                        .mistranslations(new ArrayList<>())
                        .aiReviewReport(DraftAnalysisResult.AiReviewReport.builder()
                                .summary("Patch analysis response was unstable, so this run returned the washed draft only.")
                                .build())
                        .build();
            }

            throw e;
        }
    }

    private String sanitizeOpenAiPromptText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }

        StringBuilder sanitized = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);

            if (Character.isHighSurrogate(current)) {
                if (i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                    sanitized.append(current).append(text.charAt(i + 1));
                    i++;
                }
                continue;
            }

            if (Character.isLowSurrogate(current)) {
                continue;
            }

            if (Character.isISOControl(current) && current != '\n' && current != '\r' && current != '\t') {
                continue;
            }

            sanitized.append(current);
        }

        return sanitized.toString();
    }

    private boolean isQuotaError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("insufficient_quota")
                        || lower.contains("exceeded your current quota")
                        || lower.contains("billing details")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isTimeoutError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof java.io.InterruptedIOException
                    || current instanceof java.net.SocketTimeoutException) {
                return true;
            }

            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("timeout") || lower.contains("timed out")) {
                    return true;
                }
            }

            current = current.getCause();
        }
        return false;
    }

    private boolean isPatchAnalysisRequestError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase();
                if (lower.contains("could not parse the json body of your request")
                        || lower.contains("not valid json")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private String enforceAcceptedTitleStyle(
            String text,
            String company,
            String position,
            String question,
            String companyContext,
            String context) {
        if (text == null || text.isBlank()) {
            return text;
        }

        String normalized = normalizeTitleSpacing(text).trim();
        if (!needsTitleRewrite(normalized, company, position, question)) {
            return normalized;
        }

        try {
            WorkspaceDraftAiService.DraftResponse rewritten = workspaceDraftAiService.rewriteTitle(
                    company,
                    position,
                    question,
                    companyContext,
                    normalized,
                    context);

            String candidate = normalizeTitleSpacing(rewritten.text).trim();
            return candidate.isBlank() ? normalized : candidate;
        } catch (Exception e) {
            log.warn("Title rewrite failed. Keeping original title.", e);
            return normalized;
        }
    }

    private boolean needsTitleRewrite(String text, String company, String position, String question) {
        if (text == null || text.isBlank()) {
            return false;
        }

        int closingIndex = text.indexOf(']');
        if (!text.startsWith("[") || closingIndex <= 1) {
            return true;
        }

        String title = text.substring(1, closingIndex).trim();
        if (title.isBlank()) {
            return true;
        }

        String normalizedTitle = title.replaceAll("\\s+", "");
        String normalizedQuestion = question == null ? "" : question.replaceAll("\\s+", "");
        String normalizedCompany = company == null ? "" : company.replaceAll("\\s+", "");
        String normalizedPosition = position == null ? "" : position.replaceAll("\\s+", "");

        if (!normalizedCompany.isBlank() && normalizedTitle.contains(normalizedCompany)) {
            return true;
        }

        if (!normalizedPosition.isBlank() && normalizedTitle.contains(normalizedPosition)) {
            return true;
        }

        if (!normalizedQuestion.isBlank() && normalizedQuestion.contains(normalizedTitle)) {
            return true;
        }

        String lowered = title.toLowerCase();
        return lowered.contains("\uC9C0\uC6D0\uB3D9\uAE30")
                || lowered.contains("\uC785\uC0AC \uD6C4")
                || lowered.contains("\uC785\uC0AC\uD6C4")
                || lowered.contains("\uBAA9\uD45C")
                || lowered.contains("\uC5ED\uB7C9")
                || lowered.contains("\uD3EC\uBD80")
                || lowered.contains("\uC131\uC7A5\uACFC\uC815")
                || lowered.contains("\uC9C1\uBB34")
                || title.length() > 18;
    }

    private void normalizeAnalysis(DraftAnalysisResult analysis, String originalDraft, String washedKr,
            int findingTarget) {
        if (analysis == null) {
            return;
        }

        List<DraftAnalysisResult.Mistranslation> mistranslations = analysis.getMistranslations();
        if (mistranslations == null) {
            mistranslations = new ArrayList<>();
        }

        List<DraftAnalysisResult.Mistranslation> normalized = new ArrayList<>();
        for (DraftAnalysisResult.Mistranslation mistranslation : mistranslations) {
            String translated = safeTrim(mistranslation.getTranslated());
            if (translated.isEmpty()) {
                continue;
            }

            mistranslation.setTranslated(translated);
            mistranslation.setSuggestion(normalizeTitleSpacing(safeTrim(mistranslation.getSuggestion())));
            mistranslation.setReason(safeTrim(mistranslation.getReason()));

            if (washedKr != null) {
                HighlightSpan highlightSpan = resolveHighlightSpan(washedKr, mistranslation);
                if (highlightSpan != null
                        && isReasonableHighlightSpan(washedKr, highlightSpan.start(), highlightSpan.end())) {
                    mistranslation.setStartIndex(highlightSpan.start());
                    mistranslation.setEndIndex(highlightSpan.end());
                } else {
                    mistranslation.setStartIndex(null);
                    mistranslation.setEndIndex(null);
                }
            }

            normalized.add(mistranslation);
        }

        analysis.setMistranslations(normalized);
        supplementMistranslations(analysis, originalDraft, washedKr, findingTarget);
    }

    private int calculateFindingTarget(String washedKr) {
        if (washedKr == null || washedKr.isBlank()) {
            return 5;
        }
        // 200자당 약 1개 finding, 글자가 많을수록 더 많이 요청 (상한 15)
        return Math.max(5, Math.min(15, (washedKr.length() / 200) + 3));
    }

    private void supplementMistranslations(
            DraftAnalysisResult analysis,
            String originalDraft,
            String washedKr,
            int findingTarget) {
        if (analysis == null || washedKr == null || washedKr.isBlank()) {
            return;
        }

        List<DraftAnalysisResult.Mistranslation> mistranslations = analysis.getMistranslations();
        if (mistranslations == null) {
            mistranslations = new ArrayList<>();
            analysis.setMistranslations(mistranslations);
        }

        // Do not auto-add sentence-level fallback findings.
        // Broad sentence highlights look noisy in the UI and often obscure the real
        // phrase-level issue.
    }

    private List<String> splitSentences(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        return java.util.Arrays.stream(text.split("(?<=[.!?])\\s+|\\n+"))
                .map(String::trim)
                .filter(sentence -> !sentence.isBlank())
                .toList();
    }

    private boolean containsTranslatedSpan(List<DraftAnalysisResult.Mistranslation> mistranslations,
            String translated) {
        return mistranslations.stream()
                .map(DraftAnalysisResult.Mistranslation::getTranslated)
                .anyMatch(existing -> translated.equals(safeTrim(existing)));
    }

    private double calculateSentenceSimilarity(String originalSentence, String washedSentence) {
        List<String> originalTokens = tokenizeSentence(originalSentence);
        List<String> washedTokens = tokenizeSentence(washedSentence);

        if (originalTokens.isEmpty() || washedTokens.isEmpty()) {
            return 0.0;
        }

        Set<String> overlap = new LinkedHashSet<>(originalTokens);
        overlap.retainAll(washedTokens);

        Set<String> union = new LinkedHashSet<>(originalTokens);
        union.addAll(washedTokens);

        return union.isEmpty() ? 0.0 : (double) overlap.size() / union.size();
    }

    private List<String> tokenizeSentence(String sentence) {
        if (sentence == null || sentence.isBlank()) {
            return List.of();
        }

        return java.util.Arrays.stream(sentence.toLowerCase().split("\\s+"))
                .map(token -> token.replaceAll("[^\\p{L}\\p{N}-]", ""))
                .filter(token -> token.length() > 1)
                .toList();
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private HighlightSpan resolveHighlightSpan(String source, DraftAnalysisResult.Mistranslation mistranslation) {
        if (source == null || mistranslation == null) {
            return null;
        }

        String translated = safeTrim(mistranslation.getTranslated());
        if (translated.isEmpty()) {
            return null;
        }

        Integer providedStart = mistranslation.getStartIndex();
        Integer providedEnd = mistranslation.getEndIndex();
        if (isExactSpan(source, providedStart, providedEnd, translated)) {
            return new HighlightSpan(providedStart, providedEnd);
        }

        HighlightSpan uniqueExactSpan = findUniqueExactSpan(source, translated);
        if (uniqueExactSpan != null) {
            return uniqueExactSpan;
        }

        return findCompactEquivalentSpan(source, translated);
    }

    private boolean isExactSpan(String source, Integer start, Integer end, String translated) {
        if (source == null || translated == null || start == null || end == null) {
            return false;
        }

        if (start < 0 || end <= start || end > source.length()) {
            return false;
        }

        return source.substring(start, end).equals(translated);
    }

    private List<Integer> findExactMatchIndexes(String source, String target) {
        if (source == null || target == null || target.isBlank()) {
            return List.of();
        }

        List<Integer> matches = new ArrayList<>();
        int fromIndex = 0;
        while (fromIndex < source.length()) {
            int matchIndex = source.indexOf(target, fromIndex);
            if (matchIndex < 0) {
                break;
            }
            matches.add(matchIndex);
            fromIndex = matchIndex + 1;
        }
        return matches;
    }

    private HighlightSpan findUniqueExactSpan(String source, String target) {
        List<Integer> exactMatches = findExactMatchIndexes(source, target);
        if (exactMatches.size() != 1) {
            return null;
        }

        int start = exactMatches.get(0);
        return new HighlightSpan(start, start + target.length());
    }

    private boolean isReasonableHighlightSpan(String source, int start, int end) {
        if (source == null || start < 0 || end <= start || end > source.length()) {
            return false;
        }

        String span = source.substring(start, end).trim();
        if (span.isBlank()) {
            return false;
        }

        if (span.length() > 80) {
            return false;
        }

        int sentenceStart = start;
        while (sentenceStart > 0 && !isSentenceBoundary(source.charAt(sentenceStart - 1))) {
            sentenceStart--;
        }

        int sentenceEnd = end;
        while (sentenceEnd < source.length() && !isSentenceBoundary(source.charAt(sentenceEnd))) {
            sentenceEnd++;
        }

        String sentence = source.substring(sentenceStart, sentenceEnd).trim();
        if (sentence.isBlank()) {
            return true;
        }

        return span.length() < Math.max(20, (int) Math.ceil(sentence.length() * 0.7));
    }

    private boolean isSentenceBoundary(char ch) {
        return ch == '.' || ch == '!' || ch == '?' || ch == '\n';
    }

    private HighlightSpan findCompactEquivalentSpan(String source, String target) {
        if (source == null || target == null || target.isBlank()) {
            return null;
        }

        String compactSource = stripWhitespace(source);
        String compactTarget = stripWhitespace(target);
        if (compactTarget.isBlank()) {
            return null;
        }

        List<Integer> compactMatches = findExactMatchIndexes(compactSource, compactTarget);
        if (compactMatches.size() != 1) {
            return null;
        }

        int compactStart = compactMatches.get(0);
        int compactEndExclusive = compactStart + compactTarget.length();

        Integer start = mapCompactOffsetToSourceIndex(source, compactStart);
        Integer end = mapCompactExclusiveOffsetToSourceIndex(source, compactEndExclusive);
        if (start == null || end == null || end <= start) {
            return null;
        }

        if (!isCompactEquivalentSpan(source, start, end, target)) {
            return null;
        }

        return new HighlightSpan(start, end);
    }

    private Integer mapCompactOffsetToSourceIndex(String source, int compactOffset) {
        if (source == null || compactOffset < 0) {
            return null;
        }

        int count = 0;
        for (int i = 0; i < source.length(); i++) {
            if (!Character.isWhitespace(source.charAt(i))) {
                if (count == compactOffset) {
                    return i;
                }
                count++;
            }
        }

        return null;
    }

    private Integer mapCompactExclusiveOffsetToSourceIndex(String source, int compactOffsetExclusive) {
        if (source == null || compactOffsetExclusive <= 0) {
            return null;
        }

        int count = 0;
        for (int i = 0; i < source.length(); i++) {
            if (!Character.isWhitespace(source.charAt(i))) {
                count++;
                if (count == compactOffsetExclusive) {
                    return i + 1;
                }
            }
        }

        return null;
    }

    private boolean isCompactEquivalentSpan(String source, int start, int end, String target) {
        if (source == null || target == null || start < 0 || end <= start || end > source.length()) {
            return false;
        }

        return stripWhitespace(source.substring(start, end)).equals(stripWhitespace(target));
    }

    private String stripWhitespace(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        return value.replaceAll("\\s+", "");
    }

    private void paceProcessing() {
        // No-op: speed is preferred over staged progress pacing.
    }

    private void sendStage(SseEmitter emitter, String stage) {
        sendSse(emitter, "stage", stage);
    }

    private void sendProgress(SseEmitter emitter, String stage, String message) {
        sendStage(emitter, stage);
        sendSse(emitter, "progress", message);
    }

    private void sendSse(SseEmitter emitter, String name, Object data) {
        try {
            Object sseData = data;
            if (!(data instanceof String)) {
                sseData = objectMapper.writeValueAsString(data);
            }
            log.debug("SSE send: name={}, payloadType={}", name,
                    data == null ? "null" : data.getClass().getSimpleName());
            emitter.send(SseEmitter.event().name(name).data(sseData));
        } catch (IOException | IllegalStateException e) {
            log.warn("Failed to send SSE event: {}", name);
            throw new SseConnectionClosedException(e);
        }
    }

    private void sendComment(SseEmitter emitter, String comment) {
        try {
            emitter.send(SseEmitter.event().comment(comment));
        } catch (IOException | IllegalStateException e) {
            log.warn("Failed to send SSE comment");
            throw new SseConnectionClosedException(e);
        }
    }

    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
            // ignore
        }
    }

    private record HighlightSpan(int start, int end) {
    }

    private record HeartbeatHandle(ScheduledExecutorService scheduler, ScheduledFuture<?> future) {
        private void stop() {
            future.cancel(true);
            scheduler.shutdownNow();
        }
    }

    private record RequestedLengthDirective(int minimum, int preferredTarget) {
    }
}
