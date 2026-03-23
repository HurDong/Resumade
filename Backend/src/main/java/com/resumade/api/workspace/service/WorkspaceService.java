package com.resumade.api.workspace.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.experience.domain.Experience;
import com.resumade.api.experience.domain.ExperienceRepository;
import com.resumade.api.experience.service.ExperienceVectorRetrievalService;
import com.resumade.api.workspace.domain.WorkspaceQuestion;
import com.resumade.api.workspace.domain.WorkspaceQuestionRepository;
import com.resumade.api.workspace.dto.DraftAnalysisResult;
import com.resumade.api.workspace.dto.TitleSuggestionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
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
    private static final int MINIMUM_LENGTH_DRAFT_FAMILIES = 3;
    private static final String LENGTH_RETRY_MARKER = "[LENGTH_RETRY]";
    private static final String LENGTH_REGENERATION_MARKER = "[LENGTH_REGENERATION]";
    private static final String NO_EXTRA_USER_DIRECTIVE = "No extra user directive.";
    private static final String STAGE_RAG = "RAG";
    private static final String STAGE_DRAFT = "DRAFT";
    private static final String STAGE_WASH = "WASH";
    private static final String STAGE_PATCH = "PATCH";
    private static final String STAGE_DONE = "DONE";
    private static final double DEFAULT_TARGET_MIN_RATIO = 0.80;
    private static final double DEFAULT_TARGET_MAX_RATIO = 1.00;
    private static final int TITLE_MIN_VISIBLE_CHARS = 8;
    private static final int TITLE_MAX_VISIBLE_CHARS = 45;
    private static final List<String> GENERIC_TITLE_PATTERNS = List.of(
            "\uC131\uC7A5\uACBD\uD5D8",
            "\uBB38\uC81C\uD574\uACB0",
            "\uC791\uC5C5\uC694\uC57D",
            "\uC9C0\uC6D0\uB3D9\uAE30",
            "\uAE30\uC220\uAC15\uC810",
            "\uD504\uB85C\uC81D\uD2B8",
            "\uC131\uC7A5\uACFC\uC815",
            "\uC790\uAE30\uC18C\uAC1C",
            "\uC131\uACFC\uC694\uC57D",
            "\uC9C1\uBB34\uACBD\uD5D8",
            "\uB9AC\uB354\uC2ED\uACBD\uD5D8",
            "\uACBD\uB825\uBE44\uC804",
            "\uD504\uB85C\uC81D\uD2B8\uACBD\uD5D8",
            "\uD611\uC5C5\uC0AC\uB840",
            "\uCC45\uC784\uAC10",
            "\uB3C4\uC804\uC815\uC2E0",
            "\uC5F4\uC815",
            "\uB178\uB825",
            "\uC131\uACFC",
            "\uACBD\uD5D8");
    private static final List<String> TITLE_ACTION_SIGNALS = List.of(
            "\uD574\uACB0",
            "\uAC1C\uC120",
            "\uAD6C\uCD95",
            "\uC124\uACC4",
            "\uAD6C\uD604",
            "\uC6B4\uC601",
            "\uCD5C\uC801\uD654",
            "\uBD84\uC11D",
            "\uD611\uC5C5",
            "\uC8FC\uB3C4",
            "\uB2EC\uC131",
            "\uAC80\uC99D",
            "\uC790\uB3D9\uD654",
            "\uC548\uC815\uD654",
            "\uACE0\uB3C4\uD654",
            "\uD655\uC7A5",
            "\uC804\uD658",
            "\uAC1C\uBC1C");

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
        return experienceVectorRetrievalService.search(
                query,
                3,
                extractUsedExperienceIds(question, questionId, allExperiences),
                buildSupportingQueries(question, query));
    }

    @Transactional
    public TitleSuggestionResponse suggestTitles(Long questionId) {
        WorkspaceQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found: " + questionId));

        String currentDraft = preferredQuestionDraft(question);
        if (currentDraft == null || currentDraft.isBlank()) {
            throw new IllegalStateException("Draft is empty");
        }

        String company = question.getApplication().getCompanyName();
        String position = question.getApplication().getPosition();
        String companyContext = buildApplicationResearchContext(question);
        List<Experience> allExperiences = experienceRepository.findAll();
        String context = buildFilteredContext(question, questionId, allExperiences);
        String others = buildOthersContext(question, questionId, allExperiences);

        List<TitleSuggestionResponse.TitleCandidate> candidates = buildTitleSuggestionCandidates(
                currentDraft,
                company,
                position,
                question.getTitle(),
                companyContext,
                context,
                others);

        log.info("Title suggestions generated questionId={} candidateCount={} currentTitle={}",
                questionId,
                candidates.size(),
                safeSnippet(normalizeTitleLine(extractActualTitleLine(currentDraft)), 80));

        return TitleSuggestionResponse.builder()
                .currentTitle(normalizeTitleLine(extractActualTitleLine(currentDraft)))
                .candidates(candidates)
                .build();
    }

    @Transactional
    public WorkspaceQuestion applyTitleSuggestion(Long questionId, String requestedTitleLine) {
        WorkspaceQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found: " + questionId));

        String currentDraft = preferredQuestionDraft(question);
        if (currentDraft == null || currentDraft.isBlank()) {
            throw new IllegalStateException("Draft is empty");
        }

        String company = question.getApplication().getCompanyName();
        String position = question.getApplication().getPosition();
        String improvedTitleLine = normalizeTitleLine(requestedTitleLine);

        if (!isBracketTitleLine(improvedTitleLine)) {
            throw new IllegalArgumentException("Title must be a single bracketed line");
        }

        if (!isAcceptedTitleLine(improvedTitleLine, company, position, question.getTitle())) {
            throw new IllegalArgumentException("Title does not meet title quality rules");
        }

        boolean changed = false;
        int maxLength = question.getMaxLength() == null ? 0 : question.getMaxLength();

        if (question.getContent() != null && !question.getContent().isBlank()) {
            String updatedContent = applyTitleLine(question.getContent(), improvedTitleLine);
            if ((maxLength <= 0 || countResumeCharacters(updatedContent) <= maxLength)
                    && !updatedContent.equals(question.getContent())) {
                question.setContent(updatedContent);
                changed = true;
            }
        }

        if (question.getWashedKr() != null && !question.getWashedKr().isBlank()) {
            String updatedWashed = applyTitleLine(question.getWashedKr(), improvedTitleLine);
            if ((maxLength <= 0 || countResumeCharacters(updatedWashed) <= maxLength)
                    && !updatedWashed.equals(question.getWashedKr())) {
                question.setWashedKr(updatedWashed);
                changed = true;
            }
        }

        if (!changed && (question.getContent() == null || question.getContent().isBlank())) {
            String updatedContent = applyTitleLine(currentDraft, improvedTitleLine);
            if (maxLength <= 0 || countResumeCharacters(updatedContent) <= maxLength) {
                question.setContent(updatedContent);
                changed = true;
            }
        }

        if (changed) {
            question = questionRepository.save(question);
        }

        return question;
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

            sendProgress(emitter, STAGE_RAG, "�??�하??기업 ?�보?? 문항???��?�?초안 컨텍?�트�?구성?�고 ?�어?? ?��");
            sendProgress(emitter, STAGE_RAG, "?�른 문항�?겹치�? ?�도�?경험 ?�이?��? ?��??�게 조정 중입?�다. ?��");

            List<Experience> allExperiences = experienceRepository.findAll();
            String others = buildOthersContext(initialQuestion, questionId, allExperiences);
            String context = buildFilteredContext(initialQuestion, questionId, allExperiences);

            paceProcessing();
            sendProgress(emitter, STAGE_DRAFT, "?�택??경험�??�청?�항??반영?�여 초안???�시 ?�성?�고 ?�습?�다. ?�️");

            int maxLength = initialQuestion.getMaxLength();
            int[] targetRange = resolveTargetRange(
                    maxLength,
                    directive,
                    targetChars,
                    DEFAULT_TARGET_MIN_RATIO,
                    DEFAULT_TARGET_MAX_RATIO);
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

            String pipelineRefinedDraft = expandToMinimumLength(
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
            String rawRefinedDraft = pipelineRefinedDraft;
            sendMinimumLengthWarningIfNeeded(emitter, rawRefinedDraft, minTargetChars);
            rawRefinedDraft = applyTitleLine(
                    rawRefinedDraft,
                    resolveImprovedTitleLine(
                            rawRefinedDraft,
                            company,
                            position,
                            questionTitle,
                            companyContext,
                            context,
                            others,
                            false));
            String refinedDraft = prepareDraftForTranslation(
                    rawRefinedDraft,
                    maxLength,
                    minTargetChars,
                    maxTargetChars,
                    company,
                    position,
                    companyContext,
                    context,
                    others);
            refinedDraft = selectBetterDraft(refinedDraft, rawRefinedDraft, minTargetChars, maxTargetChars, maxLength);
            refinedDraft = selectBetterDraft(refinedDraft, pipelineRefinedDraft, minTargetChars, maxTargetChars, maxLength);

            WorkspaceQuestion question = questionRepository.findById(questionId).orElseThrow();
            question.setContent(refinedDraft);
            question.setUserDirective(directive);
            questionRepository.save(question);
            sendSse(emitter, "draft_intermediate", refinedDraft);

            paceProcessing();
            sendProgress(emitter, STAGE_WASH, "기계?�인 말투�?�??�기 ?�해 1�?�?�?EN)??진행 중입?�다. ?��");
            String translatedEn = translationService.translateToEnglish(refinedDraft);

            paceProcessing();
            sendProgress(emitter, STAGE_WASH, "?�연?�러???�국??문장?�로 컴파?�하???�탁본을 ?�성 중입?�다. ?��");
            String washedKr = prepareWashedDraft(
                    translationService.translateToKorean(translatedEn));

            question = questionRepository.findById(questionId).orElseThrow();
            question.setWashedKr(washedKr);
            questionRepository.save(question);
            sendSse(emitter, "washed_intermediate", washedKr);

            paceProcessing();
            sendProgress(emitter, STAGE_PATCH, "?�탁??문장?�서 ?��?�? �??�거???�색??�?분을 '?�먼?�치'�??��? 중입?�다. ?��");
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
            normalizeAnalysis(analysis);

            question = questionRepository.findById(questionId).orElseThrow();
            question.setMistranslations(objectMapper.writeValueAsString(analysis.getMistranslations()));
            question.setAiReview(objectMapper.writeValueAsString(analysis.getAiReviewReport()));

            String responseDraft = (analysis.getHumanPatchedText() != null && !analysis.getHumanPatchedText().isBlank())
                    ? analysis.getHumanPatchedText()
                    : washedKr;
            responseDraft = prepareDraftForTranslation(
                    normalizeTitleSpacing(responseDraft),
                    maxLength,
                    minTargetChars,
                    maxTargetChars,
                    company,
                    position,
                    companyContext,
                    context,
                    others);
            responseDraft = selectBetterDraft(responseDraft, washedKr, minTargetChars, maxTargetChars, maxLength);
            responseDraft = selectBetterDraft(responseDraft, refinedDraft, minTargetChars, maxTargetChars, maxLength);

            Map<String, Object> result = buildCompletionResult(
                    refinedDraft,
                    minTargetChars,
                    washedKr,
                    responseDraft,
                    analysis);
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

            sendProgress(emitter, STAGE_RAG, "?�기?�개???�성???�해 기업 분석 ?�이?��? 문항??�?비하�??�어?? ?��");

            paceProcessing();

            sendProgress(emitter, STAGE_RAG, "??? ???? ??? ?? ??? ?? ??? ???? ????.");

            List<Experience> allExperiences = experienceRepository.findAll();
            String others = buildOthersContext(initialQuestion, questionId, allExperiences);

            sendProgress(emitter, STAGE_RAG, "문항??�?????맞는 ?�만???�심 경험???�정?�고 ?�어?? ?��");

            String context = buildFilteredContext(initialQuestion, questionId, allExperiences);

            paceProcessing();
            sendProgress(emitter, STAGE_DRAFT, "?�선??경험 ?�이?��? 바탕?�로 ?�로??초안???�성 중입?�다. ??");

            int maxLengthGen = initialQuestion.getMaxLength();
            String rawDirective = initialQuestion.getUserDirective();
            String directiveForPrompt = useDirective
                    ? augmentDirectiveForPrompt(rawDirective, maxLengthGen, targetChars)
                    : NO_EXTRA_USER_DIRECTIVE;
            int[] targetRange = resolveTargetRange(
                    maxLengthGen,
                    rawDirective,
                    targetChars,
                    DEFAULT_TARGET_MIN_RATIO,
                    DEFAULT_TARGET_MAX_RATIO);
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

            String pipelineDraft = expandToMinimumLength(
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
            String rawDraft = pipelineDraft;
            logTraceLength("humanPatch.generate.expanded", rawDraft, maxLengthGen, minTargetChars, preferredTargetChars);
            sendMinimumLengthWarningIfNeeded(emitter, rawDraft, minTargetChars);
            rawDraft = applyTitleLine(
                    rawDraft,
                    resolveImprovedTitleLine(
                            rawDraft,
                            company,
                            position,
                            questionTitle,
                            companyContext,
                            context,
                            others,
                            false));
            logTraceLength("humanPatch.generate.titleApplied", rawDraft, maxLengthGen, minTargetChars, preferredTargetChars);
            String draft = prepareDraftForTranslation(
                    rawDraft,
                    maxLengthGen,
                    minTargetChars,
                    preferredTargetChars,
                    company,
                    position,
                    companyContext,
                    context,
                    others);
            draft = selectBetterDraft(draft, rawDraft, minTargetChars, preferredTargetChars, maxLengthGen);
            draft = selectBetterDraft(draft, pipelineDraft, minTargetChars, preferredTargetChars, maxLengthGen);
            logTraceLength("humanPatch.generate.translationInput", draft, maxLengthGen, minTargetChars, preferredTargetChars);

            WorkspaceQuestion question = questionRepository.findById(questionId).orElseThrow();
            question.setContent(draft);
            questionRepository.save(question);
            sendSse(emitter, "draft_intermediate", draft);

            paceProcessing();
            sendProgress(emitter, STAGE_WASH, "기계?�인 말투�??�거?�기 ?�해 1�?번역 공정??진행?�고 ?�습?�다. ?��");
            String translatedEn = translationService.translateToEnglish(draft);
            logTraceLength("humanPatch.wash.translatedEn", translatedEn, 0, 0, 0);

            paceProcessing();
            sendProgress(emitter, STAGE_WASH, "?�연?�러???�국??문장?�로 컴파?�하???�탁본을 ?�성 중입?�다. ?��");
            String washedKr = prepareWashedDraft(
                    translationService.translateToKorean(translatedEn));
            logTraceLength("humanPatch.wash.washedKr", washedKr, maxLengthGen, minTargetChars, preferredTargetChars);

            question = questionRepository.findById(questionId).orElseThrow();
            question.setWashedKr(washedKr);
            questionRepository.save(question);
            sendSse(emitter, "washed_intermediate", washedKr);

            paceProcessing();
            sendProgress(emitter, STAGE_PATCH, "?�욱 ?�람 ?�새 ?�는 �????�해 '?�먼?�치' 분석??진행?�고 ?�어?? ?��");
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
            normalizeAnalysis(analysis);
            if (analysis != null) {
                logTraceLength("humanPatch.patch.humanPatchedText", analysis.getHumanPatchedText(), maxLengthFinal, minTargetChars, preferredTargetChars);
            }

            question = questionRepository.findById(questionId).orElseThrow();
            question.setMistranslations(objectMapper.writeValueAsString(analysis.getMistranslations()));
            question.setAiReview(objectMapper.writeValueAsString(analysis.getAiReviewReport()));

            String responseDraft = (analysis.getHumanPatchedText() != null && !analysis.getHumanPatchedText().isBlank())
                    ? analysis.getHumanPatchedText()
                    : washedKr;
            logTraceLength("humanPatch.final.beforeLengthLimit", responseDraft, maxLengthFinal, minTargetChars, preferredTargetChars);
            responseDraft = prepareDraftForTranslation(
                    normalizeTitleSpacing(responseDraft),
                    maxLengthFinal,
                    minTargetChars,
                    preferredTargetChars,
                    company,
                    position,
                    companyContext,
                    context,
                    others);
            responseDraft = selectBetterDraft(responseDraft, washedKr, minTargetChars, preferredTargetChars, maxLengthFinal);
            responseDraft = selectBetterDraft(responseDraft, draft, minTargetChars, preferredTargetChars, maxLengthFinal);
            logTraceLength("humanPatch.final.afterLengthLimit", responseDraft, maxLengthFinal, minTargetChars, preferredTargetChars);

            Map<String, Object> result = buildCompletionResult(
                    draft,
                    minTargetChars,
                    washedKr,
                    responseDraft,
                    analysis);
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
            draft = applyTitleLine(
                    draft,
                    resolveImprovedTitleLine(
                            draft,
                            company,
                            position,
                            questionTitle,
                            companyContext,
                            context,
                            others,
                            false));
            if (!draft.equals(question.getContent())) {
                question.setContent(draft);
                questionRepository.save(question);
            }

            sendComment(emitter, "flush buffer");
            sendProgress(emitter, STAGE_DRAFT, "?�재 초안??바탕?�로 ?�탁 ?�이?�라?�을 ?�시 ?�작?�니?? ?��");
            sendSse(emitter, "draft_intermediate", draft);

            paceProcessing();
            sendProgress(emitter, STAGE_WASH, "초안 고도?��? ?�해 중간 번역 과정??거치�??�습?�다. ?��");
            String translatedEn = translationService.translateToEnglish(draft);

            paceProcessing();
            sendProgress(emitter, STAGE_WASH, "?�국?�로 ?�시 번역?�며 �????��? 최적?�하�??�습?�다. ?��");

            int maxLength = question.getMaxLength();
            String rawDirective = question.getUserDirective();
            String directiveForPrompt = augmentDirectiveForPrompt(rawDirective, maxLength, null);
            int[] targetRange = resolveTargetRange(
                    maxLength,
                    rawDirective,
                    null,
                    DEFAULT_TARGET_MIN_RATIO,
                    DEFAULT_TARGET_MAX_RATIO);
            int minTargetChars = targetRange[0];
            int preferredTargetChars = targetRange[1];

            String washedKr = prepareWashedDraft(
                    translationService.translateToKorean(translatedEn));

            question = questionRepository.findById(questionId).orElseThrow();
            question.setWashedKr(washedKr);
            questionRepository.save(question);
            sendSse(emitter, "washed_intermediate", washedKr);

            paceProcessing();
            sendProgress(emitter, STAGE_PATCH, "?�제??문장??바탕?�로 ?�먼?�치 분석???�시 ?�행?�니?? ?��");
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
            sendProgress(emitter, STAGE_PATCH, "?�분?�을 ?�해 초안�??�탁본을 불러?�고 ?�습?�다. ?��");
            sendSse(emitter, "draft_intermediate", draft);
            sendSse(emitter, "washed_intermediate", washedKr);

            paceProcessing();
            sendProgress(emitter, STAGE_PATCH, "???�벽??문장???�해 ?�심 ?�치 ?�인?��? ?�시 분석?�니?? ?��");
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
        normalizeAnalysis(analysis);

        WorkspaceQuestion question = questionRepository.findById(questionId).orElseThrow();
        question.setMistranslations(objectMapper.writeValueAsString(analysis.getMistranslations()));
        question.setAiReview(objectMapper.writeValueAsString(analysis.getAiReviewReport()));
        question.setWashedKr(washedKr);
        questionRepository.save(question);

        String responseDraft = (analysis.getHumanPatchedText() != null && !analysis.getHumanPatchedText().isBlank())
                ? analysis.getHumanPatchedText()
                : washedKr;
        int[] finalTargetRange = resolveTargetRange(
                maxLength,
                null,
                null,
                DEFAULT_TARGET_MIN_RATIO,
                DEFAULT_TARGET_MAX_RATIO);
        responseDraft = prepareDraftForTranslation(
                normalizeTitleSpacing(responseDraft),
                maxLength,
                finalTargetRange[0],
                finalTargetRange[1],
                company,
                position,
                companyContext,
                context,
                others);
        responseDraft = selectBetterDraft(responseDraft, washedKr, finalTargetRange[0], finalTargetRange[1], maxLength);
        responseDraft = selectBetterDraft(responseDraft, question.getContent(), finalTargetRange[0], finalTargetRange[1], maxLength);
        Map<String, Object> result = buildCompletionResult(
                question.getContent(),
                finalTargetRange[0],
                washedKr,
                responseDraft,
                analysis);
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
        String others = initialQuestion.getApplication().getQuestions().stream()
                .filter(q -> !q.getId().equals(questionId))
                .map(q -> {
                    String draft = preferredQuestionDraft(q);
                    String usedProjects = detectUsedProjects(draft, allExperiences);
                    String titleLine = extractTitleLine(draft);
                    String bodySnippet = summarizeDraftForOverlap(draft);
                    return """
                            [OTHER_QUESTION]
                            Question: %s
                            Used projects: %s
                            Title used: %s
                            Body snippet: %s
                            Avoid reusing the same main project, title, first-sentence claim, or action-result arc for the current question unless the user explicitly requires it.
                            """.formatted(
                            safeSnippet(q.getTitle(), 180),
                            safeSnippet(usedProjects, 180),
                            safeSnippet(titleLine, 120),
                            safeSnippet(bodySnippet, 320));
                })
                .collect(Collectors.joining("\n"));

        return others.isBlank() ? "[OTHER_QUESTION]\nNo other question drafts available." : others;
    }

    private String preferredQuestionDraft(WorkspaceQuestion question) {
        if (question.getWashedKr() != null && !question.getWashedKr().isBlank()) {
            return question.getWashedKr();
        }
        if (question.getContent() != null && !question.getContent().isBlank()) {
            return question.getContent();
        }
        return "";
    }

    private String detectUsedProjects(String draft, List<Experience> allExperiences) {
        if (draft == null || draft.isBlank()) {
            return "None detected";
        }

        String matches = allExperiences.stream()
                .map(Experience::getTitle)
                .filter(title -> title != null && !title.isBlank() && draft.contains(title))
                .distinct()
                .collect(Collectors.joining(", "));

        return matches.isBlank() ? "None detected" : matches;
    }

    private String extractTitleLine(String draft) {
        if (draft == null || draft.isBlank()) {
            return "No title";
        }

        String firstLine = draft.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .findFirst()
                .orElse("");

        return firstLine.isBlank() ? "No title" : firstLine;
    }

    private String extractActualTitleLine(String draft) {
        String extracted = extractTitleLine(draft);
        return "No title".equals(extracted) ? "" : extracted;
    }

    private String resolveImprovedTitleLine(
            String draft,
            String company,
            String position,
            String questionTitle,
            String companyContext,
            String context,
            String others,
            boolean forceRewrite) {
        String normalizedDraft = normalizeLengthText(normalizeTitleSpacing(draft));
        if (normalizedDraft == null || normalizedDraft.isBlank()) {
            return "";
        }

        String currentTitleLine = normalizeTitleLine(extractActualTitleLine(normalizedDraft));
        if (!forceRewrite && isAcceptedTitleLine(currentTitleLine, company, position, questionTitle)) {
            return currentTitleLine;
        }

        try {
            WorkspaceDraftAiService.DraftResponse rewritten = workspaceDraftAiService.rewriteTitle(
                    safeTrim(company),
                    safeTrim(position),
                    safeTrim(questionTitle),
                    safeTrim(companyContext),
                    normalizedDraft.trim(),
                    buildTitleRewriteContext(context, others));

            if (rewritten == null || rewritten.text == null || rewritten.text.isBlank()) {
                return currentTitleLine;
            }

            String candidateTitleLine = normalizeTitleLine(extractActualTitleLine(rewritten.text));
            if (isAcceptedTitleLine(candidateTitleLine, company, position, questionTitle)) {
                return candidateTitleLine;
            }
        } catch (Exception e) {
            log.warn("Title rewrite failed for company={} position={} question={}",
                    safeTrim(company),
                    safeTrim(position),
                    safeTrim(questionTitle),
                    e);
        }

        return currentTitleLine;
    }

    private String buildTitleRewriteContext(String context, String others) {
        StringBuilder builder = new StringBuilder();

        String normalizedContext = safeTrim(context);
        if (!normalizedContext.isBlank()) {
            builder.append(normalizedContext);
        }

        String normalizedOthers = safeTrim(others);
        if (!normalizedOthers.isBlank()) {
            if (builder.length() > 0) {
                builder.append("\n---\n");
            }
            builder.append("[Other question titles to avoid overlapping with]\n")
                    .append(safeSnippet(normalizedOthers, 1400));
        }

        if (builder.length() == 0) {
            return "No supporting title context available.";
        }

        return builder.toString();
    }

    private List<TitleSuggestionResponse.TitleCandidate> buildTitleSuggestionCandidates(
            String draft,
            String company,
            String position,
            String questionTitle,
            String companyContext,
            String context,
            String others) {
        Map<String, TitleSuggestionResponse.TitleCandidate> deduped = new HashMap<>();
        int filteredOutCount = 0;

        try {
            WorkspaceDraftAiService.TitleCandidatesResponse response = workspaceDraftAiService.suggestTitles(
                    safeTrim(company),
                    safeTrim(position),
                    safeTrim(questionTitle),
                    safeTrim(companyContext),
                    normalizeLengthText(normalizeTitleSpacing(draft)).trim(),
                    buildTitleRewriteContext(context, others));

            if (response != null && response.candidates != null) {
                for (WorkspaceDraftAiService.TitleCandidate candidate : response.candidates) {
                    if (candidate == null) {
                        continue;
                    }

                    String titleLine = normalizeTitleLine(candidate.title);
                    String rejectionReason = findTitleRejectionReason(titleLine, company, position, questionTitle);
                    if (rejectionReason != null) {
                        filteredOutCount++;
                        log.info("Title candidate rejected source=ai title={} reason={}",
                                safeSnippet(titleLine, 120),
                                rejectionReason);
                        continue;
                    }

                    String dedupeKey = normalizeTitleComparison(extractBracketTitleCore(titleLine));
                    if (dedupeKey.isBlank() || deduped.containsKey(dedupeKey)) {
                        continue;
                    }

                    deduped.put(dedupeKey, TitleSuggestionResponse.TitleCandidate.builder()
                            .title(titleLine)
                            .score(Math.max(0, Math.min(100, candidate.score == null ? 0 : candidate.score)))
                            .reason(safeTrim(candidate.reason))
                            .recommended(false)
                            .build());
                }
            }
        } catch (Exception e) {
            log.warn("Title suggestion generation failed for company={} position={} question={}",
                    safeTrim(company),
                    safeTrim(position),
                    safeTrim(questionTitle),
                    e);
        }

        String improvedTitleLine = resolveImprovedTitleLine(
                draft,
                company,
                position,
                questionTitle,
                companyContext,
                context,
                others,
                true);
        addTitleSuggestionIfEligible(deduped, improvedTitleLine, company, position, questionTitle, 96,
                "\uD604\uC7AC \uBB38\uD56D\uACFC \uCD08\uC548 \uD750\uB984\uC5D0 \uAC00\uC7A5 \uC548\uC815\uC801\uC73C\uB85C \uB9DE\uB294 \uCD94\uCC9C \uC81C\uBAA9\uC785\uB2C8\uB2E4.");
        addTitleSuggestionIfEligible(deduped, normalizeTitleLine(extractActualTitleLine(draft)), company, position,
                questionTitle, 72, "\uD604\uC7AC \uCD08\uC548\uC758 \uB9E5\uB77D\uC744 \uADF8\uB300\uB85C \uC720\uC9C0\uD558\uB294 \uC81C\uBAA9\uC785\uB2C8\uB2E4.");

        List<TitleSuggestionResponse.TitleCandidate> ranked = deduped.values().stream()
                .sorted(Comparator.comparingInt(TitleSuggestionResponse.TitleCandidate::getScore).reversed())
                .limit(5)
                .collect(Collectors.toCollection(ArrayList::new));

        if (!ranked.isEmpty()) {
            ranked.get(0).setRecommended(true);
            if (ranked.get(0).getReason() == null || ranked.get(0).getReason().isBlank()) {
                ranked.get(0).setReason("\uC9C8\uBB38 \uC758\uB3C4\uC640 \uD604\uC7AC \uCD08\uC548\uC758 \uD575\uC2EC \uADFC\uAC70\uB97C \uAC00\uC7A5 \uC120\uBA85\uD558\uAC8C \uB4DC\uB7EC\uB0C5\uB2C8\uB2E4.");
            }
        } else {
            log.warn("Title suggestions resolved to zero candidates company={} position={} question={} filteredOutCount={}",
                    safeTrim(company),
                    safeTrim(position),
                    safeTrim(questionTitle),
                    filteredOutCount);
        }

        return ranked;
    }

    private void addTitleSuggestionIfEligible(
            Map<String, TitleSuggestionResponse.TitleCandidate> deduped,
            String titleLine,
            String company,
            String position,
            String questionTitle,
            int score,
            String reason) {
        String normalizedTitleLine = normalizeTitleLine(titleLine);
        String rejectionReason = findTitleRejectionReason(normalizedTitleLine, company, position, questionTitle);
        if (rejectionReason != null) {
            log.info("Title candidate rejected source=fallback title={} reason={}",
                    safeSnippet(normalizedTitleLine, 120),
                    rejectionReason);
            return;
        }

        String dedupeKey = normalizeTitleComparison(extractBracketTitleCore(normalizedTitleLine));
        if (dedupeKey.isBlank() || deduped.containsKey(dedupeKey)) {
            return;
        }

        deduped.put(dedupeKey, TitleSuggestionResponse.TitleCandidate.builder()
                .title(normalizedTitleLine)
                .score(score)
                .reason(reason)
                .recommended(false)
                .build());
    }

    private boolean isAcceptedTitleLine(
            String titleLine,
            String company,
            String position,
            String questionTitle) {
        return findTitleRejectionReason(titleLine, company, position, questionTitle) == null;
    }

    private String findTitleRejectionReason(
            String titleLine,
            String company,
            String position,
            String questionTitle) {
        if (!isBracketTitleLine(titleLine)) {
            return "not_bracket_title";
        }

        String core = extractBracketTitleCore(titleLine);
        if (core.isBlank()) {
            return "empty_core";
        }

        int visibleChars = countResumeCharacters(core);
        if (visibleChars < TITLE_MIN_VISIBLE_CHARS || visibleChars > TITLE_MAX_VISIBLE_CHARS) {
            return "length_out_of_range(" + visibleChars + ")";
        }

        String normalizedCore = normalizeTitleComparison(core);
        if (normalizedCore.isBlank()) {
            return "blank_after_normalization";
        }

        if (normalizedCore.contains("\uAE30\uD0C0") || normalizedCore.contains("\uC81C\uBAA9")) {
            return "contains_meta_word";
        }

        if (containsNormalized(normalizedCore, company) || containsNormalized(normalizedCore, position)) {
            return "contains_company_or_position";
        }

        String normalizedQuestion = normalizeTitleComparison(questionTitle);
        if (isQuestionParaphraseTitle(normalizedCore, normalizedQuestion)) {
            return "too_similar_to_question";
        }

        if (matchesGenericTitlePattern(normalizedCore)) {
            return "generic_title_pattern";
        }

        if (!hasConcreteTitleShape(core, normalizedCore)) {
            return "not_concrete_enough";
        }

        return null;
    }

    private boolean isBracketTitleLine(String titleLine) {
        if (titleLine == null) {
            return false;
        }
        String trimmed = titleLine.trim();
        return trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed.indexOf('\n') < 0;
    }

    private String extractBracketTitleCore(String titleLine) {
        if (!isBracketTitleLine(titleLine)) {
            return "";
        }
        String trimmed = titleLine.trim();
        return trimmed.substring(1, trimmed.length() - 1).trim();
    }

    private String normalizeTitleLine(String titleLine) {
        if (titleLine == null) {
            return "";
        }
        return titleLine.replace("\r", "").trim();
    }

    private String normalizeTitleComparison(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase()
                .replaceAll("[\\s\\p{Punct}\\p{IsPunctuation}]+", "")
                .trim();
    }

    private boolean containsNormalized(String normalizedTitle, String rawValue) {
        String normalizedValue = normalizeTitleComparison(rawValue);
        return normalizedValue.length() >= 2 && normalizedTitle.contains(normalizedValue);
    }

    private boolean matchesGenericTitlePattern(String normalizedTitle) {
        for (String pattern : GENERIC_TITLE_PATTERNS) {
            if (normalizedTitle.equals(pattern)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasConcreteTitleShape(String titleCore, String normalizedTitle) {
        if (titleCore.chars().anyMatch(Character::isDigit)) {
            return true;
        }

        if (titleCore.contains(":")) {
            return true;
        }

        for (String actionSignal : TITLE_ACTION_SIGNALS) {
            if (titleCore.contains(actionSignal)) {
                return true;
            }
        }

        if (containsAny(normalizedTitle,
                "\uAC1C\uBC1C",
                "\uC5D4\uC9C0\uB2C8\uC5B4",
                "\uB9E4\uB2C8\uC800",
                "\uAE30\uD68D",
                "\uC0AC\uC6A9\uC790",
                "\uBD84\uC11D",
                "\uC544\uD0A4\uD14D\uD2B8",
                "\uC5F0\uAD6C",
                "pm",
                "\uB9AC\uB354")) {
            return true;
        }

        String[] tokens = titleCore.trim().split("\\s+");
        return tokens.length >= 3;
    }

    private boolean isQuestionParaphraseTitle(String normalizedCore, String normalizedQuestion) {
        if (normalizedCore == null || normalizedCore.isBlank() || normalizedQuestion == null || normalizedQuestion.isBlank()) {
            return false;
        }

        if (normalizedQuestion.equals(normalizedCore)) {
            return true;
        }

        return normalizedCore.length() >= 14 && normalizedQuestion.contains(normalizedCore);
    }

    private String applyTitleLine(String text, String titleLine) {
        String normalizedText = normalizeLengthText(text);
        String normalizedTitleLine = normalizeTitleLine(titleLine);
        if (normalizedText == null || normalizedText.isBlank() || normalizedTitleLine.isBlank()) {
            return normalizedText == null ? null : normalizeTitleSpacing(normalizedText).trim();
        }

        String[] lines = normalizedText.split("\n", -1);
        int firstNonBlankLineIndex = -1;
        for (int index = 0; index < lines.length; index++) {
            if (!lines[index].trim().isBlank()) {
                firstNonBlankLineIndex = index;
                break;
            }
        }

        if (firstNonBlankLineIndex < 0) {
            return normalizeTitleSpacing(normalizedTitleLine).trim();
        }

        if (isBracketTitleLine(lines[firstNonBlankLineIndex].trim())) {
            String previousTitleLine = lines[firstNonBlankLineIndex].trim();
            lines[firstNonBlankLineIndex] = normalizedTitleLine;
            String updated = normalizeTitleSpacing(String.join("\n", lines)).trim();
            log.debug("TRACE_TITLE applyTitleLine mode=replace previousTitle={} newTitle={} resultChars={} snippet={}",
                    safeSnippet(previousTitleLine, 80),
                    safeSnippet(normalizedTitleLine, 80),
                    countResumeCharacters(updated),
                    safeSnippet(updated, 160));
            return updated;
        }

        String updated = normalizeTitleSpacing(normalizedTitleLine + "\n\n" + normalizedText.trim()).trim();
        log.debug("TRACE_TITLE applyTitleLine mode=prepend newTitle={} resultChars={} snippet={}",
                safeSnippet(normalizedTitleLine, 80),
                countResumeCharacters(updated),
                safeSnippet(updated, 160));
        return updated;
    }

    private String summarizeDraftForOverlap(String draft) {
        if (draft == null || draft.isBlank()) {
            return "No draft content";
        }

        String normalized = draft.replaceAll("\\s+", " ").trim();
        return safeSnippet(normalized, 320);
    }

    private String safeSnippet(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "None";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String resolveUserFacingErrorMessage(Exception e) {
        if (e instanceof IllegalStateException && e.getMessage() != null
                && e.getMessage().contains("minimum length requirement")) {
            return "?? ??? ????? ???? ?????. ????? ????? ?? ???? ?? ??? ???.";
        }
        return "?? ?? ? ??? ??????. ?? ? ?? ??? ???.";
    }

    private void sendMinimumLengthWarningIfNeeded(SseEmitter emitter, String draft, int minTargetChars) {
        if (draft == null || draft.isBlank() || minTargetChars <= 0) {
            return;
        }

        if (countResumeCharacters(draft) >= minTargetChars) {
            return;
        }

        sendSse(emitter, "warning", Map.of(
                "message", "최소 �??????�구?�항???�전??충족?��???못했�?�? �???근접??초안??기�??�로 ?�탁 결과�??�어??보여?�렸?�니??"));
    }

    private Map<String, Object> buildCompletionResult(
            String sourceDraft,
            int minTargetChars,
            String washedDraft,
            String humanPatchedDraft,
            DraftAnalysisResult analysis) {
        boolean usedFallbackDraft = sourceDraft != null
                && !sourceDraft.isBlank()
                && minTargetChars > 0
                && countResumeCharacters(sourceDraft) < minTargetChars;

        Map<String, Object> result = new HashMap<>();
        result.put("draft", washedDraft);
        result.put("humanPatched", humanPatchedDraft);
        result.put("sourceDraft", sourceDraft);
        result.put("usedFallbackDraft", usedFallbackDraft);
        result.put("fallbackDraft", usedFallbackDraft ? sourceDraft : null);
        result.put(
                "warningMessage",
                usedFallbackDraft
                        ? "최소 �????��? ?�전??충족?��???못해 �???목표??근접??초안??기�??�로 ?�탁?�습?�다."
                        : null);
        result.put("mistranslations", analysis.getMistranslations());
        result.put("aiReviewReport", analysis.getAiReviewReport());
        return result;
    }

    private int[] resolveTargetRange(
            int maxLength,
            String directive,
            Integer targetChars,
            double defaultMinRatio,
            double defaultMaxRatio) {
        RequestedLengthDirective requestedLength = resolveRequestedLengthDirective(directive, targetChars, maxLength);
        if (requestedLength != null) {
            return createRequestedTargetWindow(requestedLength, maxLength);
        }
        return createDefaultTargetWindow(maxLength, defaultMinRatio, defaultMaxRatio);
    }

    private String augmentDirectiveForPrompt(String directive, int maxLength, Integer targetChars) {
        String normalized = directive == null || directive.isBlank() ? NO_EXTRA_USER_DIRECTIVE : directive.trim();
        int[] targetRange = resolveTargetRange(
                maxLength,
                directive,
                targetChars,
                DEFAULT_TARGET_MIN_RATIO,
                DEFAULT_TARGET_MAX_RATIO);
        StringBuilder builder = new StringBuilder();
        if (!NO_EXTRA_USER_DIRECTIVE.equals(normalized)) {
            builder.append("Priority rules for this answer:\n");
            builder.append("- Treat the following user directive as the highest-priority writing instruction.\n");
            builder.append("- If it conflicts with retrieved experience emphasis, follow the user directive unless it would invent facts not present in the directive or current draft.\n");
            builder.append("- If the user directive says not to emphasize a role, technology, or project angle, suppress that emphasis even if it appears in retrieved context.\n");
            builder.append("- If the user directive names a specific project, role, or frontend/backend angle to emphasize, prioritize that framing.\n");
            builder.append("User directive details:\n");
            builder.append(normalized).append("\n");
        }

        builder.append("Length guidance:\n");
        builder.append("- Count only the value of the text field in the JSON output.\n");
        builder.append("- Do not count braces, quotes, key names, or escape characters.\n");
        builder.append("- Keep the text field between ")
                .append(targetRange[0])
                .append(" and ")
                .append(targetRange[1])
                .append(" visible characters.\n");
        if (maxLength > 0) {
            builder.append("- The hard limit is ")
                    .append(maxLength)
                    .append(" visible characters. Stay below it with margin; over-limit output is invalid.\n");
        }
        builder.append("- Recount before returning. If the answer is short, add concrete evidence and explanation instead of filler.");

        return builder.toString().trim();
    }

    private RequestedLengthDirective resolveRequestedLengthDirective(String directive, Integer targetChars, int maxLength) {
        if (targetChars != null && targetChars > 0) {
            int clamped = clampLengthTarget(targetChars, maxLength);
            return new RequestedLengthDirective(clamped, clamped);
        }

        RequestedLengthDirective requestedLength = extractRequestedLengthDirective(directive, maxLength);
        if (requestedLength == null) {
            return null;
        }
        return new RequestedLengthDirective(
                clampLengthTarget(requestedLength.minimum(), maxLength),
                clampLengthTarget(requestedLength.preferredTarget(), maxLength));
    }

    private int[] createRequestedTargetWindow(RequestedLengthDirective requestedLength, int maxLength) {
        int lower = Math.max(1, requestedLength.minimum());
        int upper = Math.max(lower, requestedLength.preferredTarget());
        if (maxLength > 0) {
            upper = Math.min(upper, maxLength);
        }
        return new int[] { lower, Math.max(lower, upper) };
    }

    private int[] createDefaultTargetWindow(int maxLength, double defaultMinRatio, double defaultMaxRatio) {
        if (maxLength <= 0) {
            return new int[] { 1, 1 };
        }

        int lower = Math.max(1, (int) Math.ceil(maxLength * defaultMinRatio));
        int upper = Math.max(lower, Math.min(maxLength, (int) Math.floor(maxLength * defaultMaxRatio)));
        return new int[] { lower, upper };
    }

    private int clampLengthTarget(int target, int maxLength) {
        int clamped = Math.max(1, target);
        if (maxLength > 0) {
            clamped = Math.min(clamped, maxLength);
        }
        return clamped;
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

        int ceiling = mentionedLengths.stream()
                .mapToInt(Integer::intValue)
                .max()
                .orElse(minimum);

        return Math.max(minimum, ceiling);
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
        int currentLength = countResumeCharacters(normalized);
        logLengthMetrics("expand", maxLength, minTargetChars, preferredTargetChars, normalized, 0);
        if (normalized.isBlank() || minTargetChars <= 0 || preferredTargetChars <= 0) {
            return normalized;
        }
        if (currentLength >= minTargetChars && currentLength <= preferredTargetChars) {
            return normalized;
        }

        String bestCandidate = normalized;
        String familyCandidate = normalized;

        for (int family = 1; family <= MINIMUM_LENGTH_DRAFT_FAMILIES; family++) {
            if (family > 1) {
                familyCandidate = regenerateFreshDraftFamily(
                        bestCandidate,
                        minTargetChars,
                        preferredTargetChars,
                        maxLength,
                        company,
                        position,
                        questionTitle,
                        companyContext,
                        context,
                        others,
                        directive,
                        family);
                logLengthMetrics("regenerate", maxLength, minTargetChars, preferredTargetChars, familyCandidate, family - 1);
                if (isBetterLengthCandidate(
                        familyCandidate,
                        bestCandidate,
                        minTargetChars,
                        preferredTargetChars,
                        maxLength)) {
                    bestCandidate = familyCandidate;
                }
                if (isWithinTargetWindow(familyCandidate, minTargetChars, preferredTargetChars)) {
                    return familyCandidate;
                }
            }

            String expandedFamilyCandidate = expandDraftFamily(
                    familyCandidate,
                    minTargetChars,
                    preferredTargetChars,
                    maxLength,
                    company,
                    position,
                    companyContext,
                    context,
                    others,
                    directive,
                    family);
            if (isBetterLengthCandidate(
                    expandedFamilyCandidate,
                    bestCandidate,
                    minTargetChars,
                    preferredTargetChars,
                    maxLength)) {
                bestCandidate = expandedFamilyCandidate;
            }
            if (isWithinTargetWindow(expandedFamilyCandidate, minTargetChars, preferredTargetChars)) {
                return expandedFamilyCandidate;
            }
        }

        int finalLength = countResumeCharacters(bestCandidate);
        log.warn("{} ?�크?�페?�스 | ?�계={} | ?�태={} | 초안�?{} | ?�도=- | �??�수={}??| 목표={}~{}??| ?�한={}??| ?�음={}",
                resolveStageIcon("DRAFT", "FAILED"),
                toKoreanStage("DRAFT"),
                toKoreanStatus("FAILED"),
                MINIMUM_LENGTH_DRAFT_FAMILIES,
                finalLength,
                minTargetChars,
                preferredTargetChars,
                maxLength,
                toKoreanNextAction("ABORT"));
        return bestCandidate;
    }

    private String expandDraftFamily(
            String seedCandidate,
            int minTargetChars,
            int preferredTargetChars,
            int maxLength,
            String company,
            String position,
            String companyContext,
            String context,
            String others,
            String directive,
            int family) {
        String candidate = normalizeLengthText(normalizeTitleSpacing(seedCandidate)).trim();

        for (int attempt = 1; attempt <= MINIMUM_LENGTH_EXPANSION_ATTEMPTS; attempt++) {
            int candidateLength = countResumeCharacters(candidate);
            if (candidateLength >= minTargetChars && candidateLength <= preferredTargetChars) {
                return candidate;
            }

            boolean underMin = candidateLength < minTargetChars;
            String stage = underMin ? "EXPAND" : "SHORTEN";
            String status = underMin ? "UNDER_MIN" : "OVER_LIMIT";
            log.warn("{} ?�크?�페?�스 | ?�계={} | ?�태={} | 초안�?{} | ?�도={} | �??�수={}??| 목표={}~{}??| ?�한={}??| ?�음={}",
                    resolveStageIcon(stage, status),
                    toKoreanStage(stage),
                    toKoreanStatus(status),
                    family,
                    attempt,
                    candidateLength,
                    minTargetChars,
                    preferredTargetChars,
                    maxLength,
                    toKoreanNextAction(underMin ? "EXPAND_RETRY" : "SHORTEN_RETRY"));
            try {
                String adjustedCandidate = underMin
                        ? expandDraftCandidate(
                                candidate,
                                candidateLength,
                                minTargetChars,
                                preferredTargetChars,
                                maxLength,
                                company,
                                position,
                                companyContext,
                                context,
                                others,
                                directive,
                                family,
                                attempt)
                        : shortenDraftCandidate(
                                candidate,
                                minTargetChars,
                                preferredTargetChars,
                                maxLength,
                                company,
                                position,
                                companyContext,
                                context,
                                others);
                if (isBetterLengthCandidate(
                        adjustedCandidate,
                        candidate,
                        minTargetChars,
                        preferredTargetChars,
                        maxLength)) {
                    candidate = adjustedCandidate;
                }
                logLengthMetrics("expand", maxLength, minTargetChars, preferredTargetChars, candidate, attempt);

                if (isWithinTargetWindow(candidate, minTargetChars, preferredTargetChars)) {
                    return candidate;
                }
            } catch (Exception e) {
                log.warn(
                        "{} ?�크?�페?�스 | ?�계={} | ?�태={} | 초안�?{} | ?�도={} | �??�수={}??| 목표={}~{}??| ?�한={}??| ?�음={} | ?�유=?�장 ?�출 ?�패",
                        resolveStageIcon("EXPAND", "ERROR"),
                        toKoreanStage("EXPAND"),
                        toKoreanStatus("ERROR"),
                        family,
                        attempt,
                        countResumeCharacters(candidate),
                        minTargetChars,
                        preferredTargetChars,
                        maxLength,
                        toKoreanNextAction("EXPAND_RETRY"),
                        e);
            }
        }

        int finalLength = countResumeCharacters(candidate);
        log.warn("{} ?�크?�페?�스 | ?�계={} | ?�태={} | 초안�?{} | ?�도={} | �??�수={}??| 목표={}~{}??| ?�한={}??| ?�음={}",
                resolveStageIcon("EXPAND", "FAMILY_FAILED"),
                toKoreanStage("EXPAND"),
                toKoreanStatus("FAMILY_FAILED"),
                family,
                MINIMUM_LENGTH_EXPANSION_ATTEMPTS,
                finalLength,
                minTargetChars,
                preferredTargetChars,
                maxLength,
                toKoreanNextAction("NEW_FAMILY"));
        return candidate;
    }

    private String regenerateFreshDraftFamily(
            String previousBestDraft,
            int minTargetChars,
            int preferredTargetChars,
            int maxLength,
            String company,
            String position,
            String questionTitle,
            String companyContext,
            String context,
            String others,
            String directive,
            int family) {
        int previousLength = countResumeCharacters(previousBestDraft);
        log.warn(
                "{} ?�크?�페?�스 | ?�계={} | ?�태={} | 초안�?{} | ?�도=- | �??�수={}??| 목표={}~{}??| ?�한={}??| ?�음={}",
                resolveStageIcon("REGENERATE", "START"),
                toKoreanStage("REGENERATE"),
                toKoreanStatus("START"),
                family,
                previousLength,
                minTargetChars,
                preferredTargetChars,
                maxLength,
                toKoreanNextAction("GENERATE_FRESH_FAMILY"));

        try {
            WorkspaceDraftAiService.DraftResponse regenerated = workspaceDraftAiService.generateDraft(
                    company,
                    position,
                    questionTitle,
                    companyContext,
                    maxLength,
                    minTargetChars,
                    preferredTargetChars,
                    context,
                    others,
                    directive);

            if (regenerated == null || regenerated.text == null || regenerated.text.isBlank()) {
                return previousBestDraft;
            }

            return prepareDraftForTranslation(
                    regenerated.text,
                    maxLength,
                    minTargetChars,
                    preferredTargetChars,
                    company,
                    position,
                    companyContext,
                    context,
                    others);
        } catch (Exception e) {
            log.warn(
                    "{} ?�크?�페?�스 | ?�계={} | ?�태={} | 초안�?{} | ?�도=- | �??�수={}??| 목표={}~{}??| ?�한={}??| ?�음={} | ?�유=??초안�??�성 ?�패",
                    resolveStageIcon("REGENERATE", "ERROR"),
                    toKoreanStage("REGENERATE"),
                    toKoreanStatus("ERROR"),
                    family,
                    previousLength,
                    minTargetChars,
                    preferredTargetChars,
                    maxLength,
                    toKoreanNextAction("KEEP_PREVIOUS_BEST"),
                    e);
            return previousBestDraft;
        }
    }

    private String expandDraftCandidate(
            String candidate,
            int candidateLength,
            int minTargetChars,
            int preferredTargetChars,
            int maxLength,
            String company,
            String position,
            String companyContext,
            String context,
            String others,
            String directive,
            int family,
            int attempt) {
        String expansionDirective = buildMinimumLengthDirective(
                directive,
                candidateLength,
                minTargetChars,
                preferredTargetChars,
                maxLength,
                family,
                attempt);

        WorkspaceDraftAiService.DraftResponse expanded = workspaceDraftAiService.refineDraft(
                company,
                position,
                companyContext,
                candidate,
                maxLength,
                minTargetChars,
                preferredTargetChars,
                context,
                others,
                expansionDirective);

        if (expanded == null || expanded.text == null || expanded.text.isBlank()) {
            return candidate;
        }

        return normalizeLengthText(normalizeTitleSpacing(expanded.text)).trim();
    }

    private String shortenDraftCandidate(
            String candidate,
            int minTargetChars,
            int preferredTargetChars,
            int maxLength,
            String company,
            String position,
            String companyContext,
            String context,
            String others) {
        WorkspaceDraftAiService.DraftResponse shortened = workspaceDraftAiService.shortenToLimit(
                safeTrim(company),
                safeTrim(position),
                safeTrim(companyContext),
                candidate,
                maxLength,
                safeTrim(context),
                safeTrim(others));

        if (shortened == null || shortened.text == null || shortened.text.isBlank()) {
            return hardTrimToLimit(candidate, maxLength);
        }

        String normalized = normalizeLengthText(normalizeTitleSpacing(shortened.text)).trim();
        int normalizedLength = countResumeCharacters(normalized);
        if (normalizedLength < minTargetChars) {
            return hardTrimToLimit(candidate, maxLength);
        }
        if (normalizedLength > maxLength) {
            return hardTrimToLimit(normalized, maxLength);
        }
        return normalized;
    }

    private String buildMinimumLengthDirective(
            String directive,
            int previousLength,
            int minTargetChars,
            int preferredTargetChars,
            int maxLength,
            int family,
            int attempt) {
        int minGap = Math.max(0, minTargetChars - previousLength);
        int preferredGap = Math.max(minGap, preferredTargetChars - previousLength);
        StringBuilder builder = new StringBuilder();
        builder.append(LENGTH_RETRY_MARKER).append("\n");
        if (directive != null && !directive.isBlank()) {
            builder.append(directive.trim()).append("\n");
        }
        builder.append("Retry feedback: previous output length was ")
                .append(previousLength)
                .append(" characters, which is below the minimum target.\n");
        builder.append("Draft family: ")
                .append(family)
                .append(" / ")
                .append(MINIMUM_LENGTH_DRAFT_FAMILIES)
                .append(".\n");
        builder.append("Retry attempt: ")
                .append(attempt)
                .append(" / ")
                .append(MINIMUM_LENGTH_EXPANSION_ATTEMPTS)
                .append(".\n");
        builder.append("Target text-field window for this retry: ")
                .append(minTargetChars)
                .append(" to ")
                .append(preferredTargetChars)
                .append(" visible characters.\n");
        builder.append("Hard limit: ")
                .append(maxLength)
                .append(" characters.\n");
        builder.append("Count only the value of the text field. Do not count braces, quotes, key names, or escape characters.\n");
        builder.append("Current deficit to minimum target: at least ")
                .append(minGap)
                .append(" more visible characters.\n");
        builder.append("Expansion goal for this retry: add roughly ")
                .append(preferredGap)
                .append(" visible characters so the final answer lands inside the target window.\n");
        builder.append("Preserve all strong facts from the current draft.\n");
        builder.append("Expand only missing depth. Do not summarize, compress, or delete existing strong evidence.\n");
        builder.append(
                "Expansion order: background -> role -> judgment -> execution detail -> measurable result -> job connection.\n");
        builder.append(buildDynamicExpansionTactic(minGap, attempt)).append("\n");
        builder.append("Count spaces and line breaks as 1 character each. Generic filler is forbidden.");
        return builder.toString();
    }

    private String buildMinimumLengthRegenerationDirective(
            String directive,
            String previousDraft,
            int previousLength,
            int minTargetChars,
            int preferredTargetChars,
            int maxLength,
            int family) {
        int minGap = Math.max(0, minTargetChars - previousLength);
        int preferredGap = Math.max(minGap, preferredTargetChars - previousLength);
        StringBuilder builder = new StringBuilder();
        builder.append(LENGTH_REGENERATION_MARKER).append("\n");
        if (directive != null && !directive.isBlank()) {
            builder.append(directive.trim()).append("\n");
        }
        builder.append("Regeneration feedback: the previous draft stayed at ")
                .append(previousLength)
                .append(" characters and failed to reach the required range after expansion retries.\n");
        builder.append("New draft family: ")
                .append(family)
                .append(" / ")
                .append(MINIMUM_LENGTH_DRAFT_FAMILIES)
                .append(".\n");
        builder.append("Required output range for this fresh attempt: ")
                .append(minTargetChars)
                .append(" to ")
                .append(preferredTargetChars)
                .append(" visible characters.\n");
        builder.append("Hard limit: ")
                .append(maxLength)
                .append(" characters.\n");
        builder.append("You must write a fresh draft angle instead of lightly paraphrasing the failed draft.\n");
        builder.append("Keep the same facts, question intent, and anti-overlap constraints, but choose a fuller structure, stronger evidence ordering, or a different valid lead sentence so the answer naturally reaches the target window.\n");
        builder.append("At minimum, add ")
                .append(minGap)
                .append(" visible characters beyond the failed draft and aim to add about ")
                .append(preferredGap)
                .append(".\n");
        builder.append("Do not copy the failed draft sentence-by-sentence. Rebuild it from the supplied context with denser factual detail and clearer causality.\n");
        builder.append("Failed draft for reference only:\n")
                .append(previousDraft);
        return builder.toString();
    }

    private String buildDynamicExpansionTactic(int minGap, int attempt) {
        if (minGap >= 180) {
            return "This is a large deficit. Use a fuller narrative shape with two evidence-rich movements, and unpack constraints, decisions, execution detail, and outcome causality so the text grows materially without filler.";
        }
        if (minGap >= 110) {
            return "This is a medium-large deficit. Expand the current example with concrete obstacles, trade-offs, implementation detail, and the reason each action mattered to the final result.";
        }
        if (minGap >= 60) {
            return "This is a moderate deficit. Add the missing rationale, execution sequence, and result interpretation instead of repeating high-level claims.";
        }
        if (attempt >= MINIMUM_LENGTH_EXPANSION_ATTEMPTS) {
            return "This is the final expansion retry. If the current angle still feels too thin, broaden the explanation with another valid fact or sub-problem already present in the supplied context.";
        }
        return "This is a small deficit. Add only the missing factual glue, such as why the problem mattered, what judgment you made, and what changed afterward.";
    }

    private int[] resolveExpansionRetryWindow(
            int currentLength,
            int minTargetChars,
            int preferredTargetChars,
            int maxLength,
            int attempt) {
        return new int[] {
                clampLengthTarget(minTargetChars, maxLength),
                clampLengthTarget(Math.max(minTargetChars, preferredTargetChars), maxLength)
        };
    }

    private int[] resolveRegenerationRetryWindow(
            int currentLength,
            int minTargetChars,
            int preferredTargetChars,
            int maxLength) {
        return new int[] {
                clampLengthTarget(minTargetChars, maxLength),
                clampLengthTarget(Math.max(minTargetChars, preferredTargetChars), maxLength)
        };
    }

    private boolean isWithinTargetWindow(String text, int minTargetChars, int preferredTargetChars) {
        int length = countResumeCharacters(text);
        return length >= minTargetChars && length <= preferredTargetChars;
    }

    private boolean isBetterLengthCandidate(
            String challenger,
            String incumbent,
            int minTargetChars,
            int preferredTargetChars,
            int maxLength) {
        return resolveLengthFitnessScore(challenger, minTargetChars, preferredTargetChars, maxLength)
                > resolveLengthFitnessScore(incumbent, minTargetChars, preferredTargetChars, maxLength);
    }

    private int resolveLengthFitnessScore(String text, int minTargetChars, int preferredTargetChars, int maxLength) {
        int length = countResumeCharacters(text);
        if (length >= minTargetChars && length <= preferredTargetChars) {
            return 2_000_000 + length;
        }

        int distanceToWindow = length < minTargetChars
                ? (minTargetChars - length)
                : (length - preferredTargetChars);
        int hardLimitPenalty = maxLength > 0 && length > maxLength ? 100_000 : 0;
        int overTargetPenalty = length > preferredTargetChars ? 10_000 : 0;
        return 1_000_000 - hardLimitPenalty - overTargetPenalty - distanceToWindow;
    }

    private String selectBetterDraft(
            String primary,
            String alternative,
            int minTargetChars,
            int preferredTargetChars,
            int maxLength) {
        if (alternative == null || alternative.isBlank()) {
            return primary;
        }
        if (primary == null || primary.isBlank()) {
            return alternative;
        }
        return isBetterLengthCandidate(alternative, primary, minTargetChars, preferredTargetChars, maxLength)
                ? alternative
                : primary;
    }

    private String buildApplicationResearchContext(WorkspaceQuestion question) {
        List<String> sections = new ArrayList<>();

        sections.add(buildQuestionIntentContext(question));

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

    private String buildQuestionIntentContext(WorkspaceQuestion question) {
        String title = safeTrim(question.getTitle());
        String normalized = title.toLowerCase();

        String type = "JOB_FIT";
        String primaryFocus = "?? ???, ?? ??, ?? ?? ??";
        String weightingRule = "Use JD and company context as the primary rubric. Select evidence that directly proves role fit.";

        if (containsAny(normalized, "??", "???", "??", "??", "??", "??????",
                "??", "??", "??", "teamwork", "collaboration", "communication", "conflict")) {
            type = "COLLABORATION";
            primaryFocus = "?? ??, ?? ??, ??????, ?? ?? ?? ??";
            weightingRule = "Prioritize the questions collaboration intent first, then factual evidence, and use JD only as a secondary tie-back. Do not force a pure job-skill essay when the question is mainly about teamwork or conflict.";
        } else if (containsAny(normalized, "??", "??", "???", "??", "??", "??", "??", "??",
                "??", "trouble", "challenge", "problem", "failure")) {
            type = "PROBLEM_SOLVING";
            primaryFocus = "?? ??, ?? ??, ??, ?? ??, ??";
            weightingRule = "Prioritize the questions problem-solving intent first. Use JD as supporting context only if it sharpens why the response matters for the role.";
        } else if (containsAny(normalized, "??", "??", "??", "??", "??", "??", "??",
                "learn", "growth", "develop")) {
            type = "GROWTH";
            primaryFocus = "?? ???, ?? ??, ??? ??, ??";
            weightingRule = "Prioritize the growth narrative first. Use JD as a secondary bridge to show why that growth matters for the target role.";
        } else if (containsAny(normalized, "????", "?", "? ??", "?? ??", "?? ?", "??",
                "??", "motivation", "why our company", "future")) {
            type = "MOTIVATION";
            primaryFocus = "?? ?? ??, ?? ???, ?? ? ?? ??";
            weightingRule = "Use company context and JD as the primary rubric. Connect concrete past evidence to why the company and role are a logical next step.";
        } else if (containsAny(normalized, "??", "????", "??", "??", "??", "??", "??",
                "value", "belief", "strength")) {
            type = "VALUE_FIT";
            primaryFocus = "???, ?? ??, ??? ??, ??? ?? ??";
            weightingRule = "Prioritize the value or attitude asked by the question first. Use JD as a secondary alignment check rather than the main storyline.";
        }

        return """
                [Question Intent]
                Question: %s
                Intent type: %s
                Primary focus: %s
                Weighting rule: %s
                """.formatted(
                title.isBlank() ? "No question title provided." : title,
                type,
                primaryFocus,
                weightingRule);
    }

    private boolean containsAny(String source, String... needles) {
        if (source == null || source.isBlank()) {
            return false;
        }

        for (String needle : needles) {
            if (needle != null && !needle.isBlank() && source.contains(needle.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private List<String> buildSupportingQueries(WorkspaceQuestion question, String primaryQuery) {
        if (question == null || question.getApplication() == null) {
            return List.of();
        }

        List<String> queries = new ArrayList<>();
        String title = safeTrim(question.getTitle());
        String company = safeTrim(question.getApplication().getCompanyName());
        String position = safeTrim(question.getApplication().getPosition());
        String userDirective = compactQueryText(question.getUserDirective(), 180);
        String jdInsight = compactQueryText(question.getApplication().getAiInsight(), 220);
        String companyResearch = compactQueryText(question.getApplication().getCompanyResearch(), 220);
        String rawJd = compactQueryText(question.getApplication().getRawJd(), 220);

        addSupportingQuery(queries, joinQueryParts(title, position));
        addSupportingQuery(queries, joinQueryParts(title, company, position));
        addSupportingQuery(queries, joinQueryParts(title, position, userDirective));
        addSupportingQuery(queries, joinQueryParts(title, position, jdInsight, companyResearch));
        addSupportingQuery(queries, joinQueryParts(title, position, rawJd));

        String normalizedPrimary = safeTrim(primaryQuery);
        return queries.stream()
                .filter(query -> !query.equalsIgnoreCase(normalizedPrimary))
                .limit(4)
                .toList();
    }

    private void addSupportingQuery(List<String> target, String query) {
        String normalized = safeTrim(query);
        if (normalized.isBlank()) {
            return;
        }

        boolean exists = target.stream().anyMatch(existing -> existing.equalsIgnoreCase(normalized));
        if (!exists) {
            target.add(normalized);
        }
    }

    private String joinQueryParts(String... parts) {
        return java.util.Arrays.stream(parts)
                .map(this::safeTrim)
                .filter(part -> !part.isBlank())
                .collect(Collectors.joining(" | "));
    }

    private String compactQueryText(String text, int maxLength) {
        String normalized = safeTrim(text)
                .replaceAll("\\s+", " ");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength).trim();
    }

    private String buildFilteredContext(WorkspaceQuestion initialQuestion, Long questionId,
            List<Experience> allExperiences) {
        Set<Long> excludedExperienceIds = extractUsedExperienceIds(initialQuestion, questionId, allExperiences);
        List<com.resumade.api.workspace.dto.ExperienceContextResponse.ContextItem> selectedContext = experienceVectorRetrievalService
                .search(
                        initialQuestion.getTitle(),
                        4,
                        excludedExperienceIds,
                        buildSupportingQueries(initialQuestion, initialQuestion.getTitle()));

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
            int minTargetChars,
            int preferredTargetChars,
            String company,
            String position,
            String companyContext,
            String context,
            String others) {
        if (text == null) {
            return null;
        }

        String normalized = normalizeLengthText(normalizeTitleSpacing(text)).trim();
        logTraceLength("lengthLimit.input", normalized, maxLength, 0, 0);
        if (maxLength <= 0 || countResumeCharacters(normalized) <= maxLength) {
            return normalized;
        }

        int[] defaultRange = new int[] {
                Math.max(1, minTargetChars),
                Math.max(Math.max(1, minTargetChars), preferredTargetChars > 0 ? preferredTargetChars : maxLength)
        };
        logLengthMetrics("shorten", maxLength, defaultRange[0], defaultRange[1], normalized, 0);
        log.warn("{} ?�크?�페?�스 | ?�계={} | ?�태={} | 초안�?- | ?�도=0 | �??�수={}??| 목표={}~{}??| ?�한={}??| ?�음={}",
                resolveStageIcon("SHORTEN", "OVER_LIMIT"),
                toKoreanStage("SHORTEN"),
                toKoreanStatus("OVER_LIMIT"),
                countResumeCharacters(normalized),
                defaultRange[0],
                defaultRange[1],
                maxLength,
                toKoreanNextAction("SHORTEN_RETRY"));

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
                log.warn("{} ?�크?�페?�스 | ?�계={} | ?�태={} | 초안�?- | ?�도=1 | �??�수={}??| 목표={}~{}??| ?�한={}??| ?�음={}",
                        resolveStageIcon("SHORTEN", "EMPTY_RESULT"),
                        toKoreanStage("SHORTEN"),
                        toKoreanStatus("EMPTY_RESULT"),
                        countResumeCharacters(normalized),
                        defaultRange[0],
                        defaultRange[1],
                        maxLength,
                        toKoreanNextAction("HARD_TRIM"));
                String trimmed = hardTrimToLimit(normalized, maxLength);
                logTraceLength("lengthLimit.hardTrimFromEmpty", trimmed, maxLength, defaultRange[0], defaultRange[1]);
                logLengthMetrics("shorten", maxLength, defaultRange[0], defaultRange[1], trimmed, 2);
                return trimmed;
            }

            String candidate = normalizeLengthText(normalizeTitleSpacing(shortened.text)).trim();
            logTraceLength("lengthLimit.aiCandidate", candidate, maxLength, defaultRange[0], defaultRange[1]);
            int candidateLength = countResumeCharacters(candidate);
            if (candidateLength <= maxLength && candidateLength >= defaultRange[0]) {
                logLengthMetrics("shorten", maxLength, defaultRange[0], defaultRange[1], candidate, 1);
                return candidate;
            }

            String retryStatus = candidateLength > maxLength ? "OVER_LIMIT" : "UNDER_MIN";
            log.warn("{} ?�크?�페?�스 | ?�계={} | ?�태={} | 초안�?- | ?�도=1 | �??�수={}??| 목표={}~{}??| ?�한={}??| ?�음={}",
                    resolveStageIcon("SHORTEN", retryStatus),
                    toKoreanStage("SHORTEN"),
                    toKoreanStatus(retryStatus),
                    candidateLength,
                    defaultRange[0],
                    defaultRange[1],
                    maxLength,
                    toKoreanNextAction("HARD_TRIM"));
            String trimSource = "UNDER_MIN".equals(retryStatus) ? normalized : candidate;
            String trimmed = hardTrimToLimit(trimSource, maxLength);
            logTraceLength("lengthLimit.hardTrimFromOverLimit", trimmed, maxLength, defaultRange[0], defaultRange[1]);
            logLengthMetrics("shorten", maxLength, defaultRange[0], defaultRange[1], trimmed, 2);
            return trimmed;
        } catch (Exception e) {
            log.warn("{} ?�크?�페?�스 | ?�계={} | ?�태={} | 초안�?- | ?�도=1 | �??�수={}??| 목표={}~{}??| ?�한={}??| ?�음={} | ?�유=줄이�??�출 ?�패",
                    resolveStageIcon("SHORTEN", "ERROR"),
                    toKoreanStage("SHORTEN"),
                    toKoreanStatus("ERROR"),
                    countResumeCharacters(normalized),
                    defaultRange[0],
                    defaultRange[1],
                    maxLength,
                    toKoreanNextAction("HARD_TRIM"),
                    e);
            String trimmed = hardTrimToLimit(normalized, maxLength);
            logTraceLength("lengthLimit.hardTrimFromError", trimmed, maxLength, defaultRange[0], defaultRange[1]);
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
            int minTargetChars,
            int preferredTargetChars,
            String company,
            String position,
            String companyContext,
            String context,
            String others) {
        if (text == null) {
            return null;
        }

        String normalized = normalizeLengthText(normalizeTitleSpacing(text)).trim();
        logTraceLength("prepareDraftForTranslation.input", normalized, maxLength, minTargetChars, preferredTargetChars);
        if (maxLength > 0 && countResumeCharacters(normalized) > maxLength) {
            String shortened = enforceLengthLimit(
                    normalized,
                    maxLength,
                    minTargetChars,
                    preferredTargetChars,
                    company,
                    position,
                    companyContext,
                    context,
                    others);
            logTraceLength("prepareDraftForTranslation.output", shortened, maxLength, minTargetChars, preferredTargetChars);
            return shortened;
        }

        logTraceLength("prepareDraftForTranslation.output", normalized, maxLength, minTargetChars, preferredTargetChars);
        return normalized;
    }

    private String prepareWashedDraft(String text) {
        if (text == null) {
            return null;
        }
        return normalizeLengthText(normalizeTitleSpacing(text)).trim();
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
        String status = resolvePipelineLengthStatus(actualChars, minimumTarget, preferredTarget, hardLimit);
        String attempt = retryCount > 0 ? String.valueOf(retryCount) : "-";
        String next = resolvePipelineNextAction(stage, status);
        String normalizedStage = stage == null ? "" : stage.trim().toUpperCase();
        log.info(
                "{} ?�크?�페?�스 | ?�계={} | ?�태={} | 초안�?- | ?�도={} | �??�수={}??| 목표={}~{}??| ?�한={}??| ?�음={}",
                resolveStageIcon(normalizedStage, status),
                toKoreanStage(normalizedStage),
                toKoreanStatus(status),
                attempt,
                actualChars,
                minimumTarget,
                preferredTarget,
                hardLimit,
                toKoreanNextAction(next));
    }

    private String resolvePipelineLengthStatus(int actualChars, int minimumTarget, int preferredTarget, int hardLimit) {
        if (hardLimit > 0 && actualChars > hardLimit) {
            return "OVER_LIMIT";
        }
        if (minimumTarget > 0 && actualChars < minimumTarget) {
            return "UNDER_MIN";
        }
        if (preferredTarget > 0 && actualChars <= preferredTarget) {
            return "IN_RANGE";
        }
        return "ABOVE_PREFERRED";
    }

    private String resolvePipelineNextAction(String stage, String status) {
        String normalizedStage = stage == null ? "" : stage.trim().toUpperCase();
        return switch (normalizedStage) {
            case "GENERATE", "REFINE" -> "EXPAND_OR_CONTINUE";
            case "EXPAND" -> "CHECK_TARGET";
            case "REGENERATE" -> "CHECK_TARGET";
            case "SHORTEN" -> "CHECK_LIMIT";
            case "FINAL" -> "COMPLETE";
            default -> "CONTINUE";
        };
    }

    private String toKoreanStage(String stage) {
        return switch (stage) {
            case "GENERATE" -> "?? ??";
            case "REFINE" -> "?? ??";
            case "EXPAND" -> "?? ??";
            case "REGENERATE" -> "???";
            case "SHORTEN" -> "?? ???";
            case "FINAL" -> "?? ??";
            case "DRAFT" -> "?? ?????";
            default -> stage;
        };
    }

    private String toKoreanStatus(String status) {
        return switch (status) {
            case "UNDER_MIN" -> "?? ??? ??";
            case "IN_RANGE" -> "?? ?? ??";
            case "ABOVE_PREFERRED" -> "?? ?? ??";
            case "OVER_LIMIT" -> "?? ??? ??";
            case "FAILED" -> "??";
            case "FAMILY_FAILED" -> "??? ??";
            case "ERROR" -> "??";
            case "START" -> "??";
            case "EMPTY_RESULT" -> "?? ?? ??";
            default -> status;
        };
    }

    private String toKoreanNextAction(String next) {
        return switch (next) {
            case "EXPAND_OR_CONTINUE" -> "?? ?? ??";
            case "CHECK_TARGET" -> "?? ?? ??";
            case "CHECK_LIMIT" -> "??? ?? ??";
            case "COMPLETE" -> "?? ?? ??";
            case "ABORT" -> "????? ??";
            case "EXPAND_RETRY" -> "?? ????? ?? ??";
            case "NEW_FAMILY" -> "?? ??? ??";
            case "GENERATE_FRESH_FAMILY" -> "? ???? ?? ??";
            case "KEEP_PREVIOUS_BEST" -> "?? ??? ??";
            case "SHORTEN_RETRY" -> "??? ???";
            case "HARD_TRIM" -> "?? ???";
            case "CONTINUE" -> "?? ??";
            default -> next;
        };
    }

    private String resolveStageIcon(String stage, String status) {
        if ("ERROR".equals(status) || "FAILED".equals(status) || "FAMILY_FAILED".equals(status)) {
            return "[ERROR]";
        }
        if ("UNDER_MIN".equals(status) || "OVER_LIMIT".equals(status) || "EMPTY_RESULT".equals(status)) {
            return "[WARN]";
        }
        if ("IN_RANGE".equals(status)) {
            return "[OK]";
        }
        if ("START".equals(status)) {
            return "[RUN]";
        }
        return switch (stage) {
            case "GENERATE", "REFINE", "EXPAND", "DRAFT" -> "[DRAFT]";
            case "REGENERATE" -> "[RETRY]";
            case "SHORTEN" -> "[TRIM]";
            case "FINAL" -> "[DONE]";
            default -> "[STEP]";
        };
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
                sendProgress(emitter, STAGE_PATCH, "?�먼?�치 분석 결과�? 불안?�하???�탁본만 반환?�니?? ?�️");

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

    private void normalizeAnalysis(DraftAnalysisResult analysis) {
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
            // (Note: startIndex/endIndex manual calculation is removed as AI now provides tagged text)


            normalized.add(mistranslation);
        }

        analysis.setMistranslations(normalized);
    }

    private int calculateFindingTarget(String washedKr) {
        if (washedKr == null || washedKr.isBlank()) {
            return 5;
        }
        // 200?�당 ??1�?finding, �??��? 많을?�록 ??많이 ?�청 (?�한 15)
        return Math.max(5, Math.min(15, (washedKr.length() / 200) + 3));
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private void logTraceLength(
            String label,
            String text,
            int hardLimit,
            int minimumTarget,
            int preferredTarget) {
        if (!log.isDebugEnabled()) {
            return;
        }

        int actualChars = countResumeCharacters(text);
        String status = resolvePipelineLengthStatus(actualChars, minimumTarget, preferredTarget, hardLimit);
        log.debug(
                "TRACE_LENGTH label={} chars={} hardLimit={} minTarget={} preferredTarget={} status={} snippet={}",
                label,
                actualChars,
                hardLimit,
                minimumTarget,
                preferredTarget,
                status,
                safeSnippet(normalizeLengthText(text), 220));
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

    private record HeartbeatHandle(ScheduledExecutorService scheduler, ScheduledFuture<?> future) {
        private void stop() {
            future.cancel(true);
            scheduler.shutdownNow();
        }
    }

private record RequestedLengthDirective(int minimum, int preferredTarget) {
    }
}

