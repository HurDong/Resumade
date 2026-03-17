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
    private static final Pattern REQUESTED_LENGTH_PATTERN = Pattern.compile("(\\d{2,4})\\s*(?:글자|자)");


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

    public List<com.resumade.api.workspace.dto.ExperienceContextResponse.ContextItem> getMatchedExperiences(Long questionId, String customQuery) {
        WorkspaceQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found: " + questionId));

        String query = (customQuery != null && !customQuery.isBlank()) ? customQuery : question.getTitle();
        List<Experience> allExperiences = experienceRepository.findAll();
        return experienceVectorRetrievalService.search(query, 3, extractUsedExperienceIds(question, questionId, allExperiences));
    }

    @Transactional(readOnly = true)
    public void processRefinement(Long questionId, String directive, SseEmitter emitter) {
        HeartbeatHandle heartbeat = startHeartbeat(emitter);
        try {
            WorkspaceQuestion initialQuestion = questionRepository.findById(questionId)
                    .orElseThrow(() -> new RuntimeException("Question not found: " + questionId));

            String company = initialQuestion.getApplication().getCompanyName();
            String position = initialQuestion.getApplication().getPosition();
            String companyContext = buildApplicationResearchContext(initialQuestion);
            String currentInput = initialQuestion.getWashedKr() != null
                    ? initialQuestion.getWashedKr()
                    : initialQuestion.getContent();

            sendSse(emitter, "progress", "🪄 기존 초안을 바탕으로 더 자연스럽고 설득력 있게 다듬고 있어요.");
            sendSse(emitter, "progress", "📚 문항과 잘 맞는 경험을 다시 찾아서 반영하고 있어요.");

            List<Experience> allExperiences = experienceRepository.findAll();
            String others = buildOthersContext(initialQuestion, questionId, allExperiences);
            String context = buildFilteredContext(initialQuestion, questionId, allExperiences);

            paceProcessing();
            sendSse(emitter, "progress", "✍️ 요청하신 방향을 반영해 문장을 더 정교하게 손보고 있어요.");

            int maxLength = initialQuestion.getMaxLength();
            int[] targetRange = resolveTargetRange(maxLength, directive, 0.9, 0.98);
            int minTargetChars = targetRange[0];
            int maxTargetChars = targetRange[1];
            String directiveForPrompt = augmentDirectiveForPrompt(directive, maxLength);

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
                    directiveForPrompt
            );

            String rawRefinedDraft = normalizeTitleSpacing(refineResponse.text).trim();
            String refinedDraft = prepareDraftForTranslation(rawRefinedDraft, maxLength);

            WorkspaceQuestion question = questionRepository.findById(questionId).orElseThrow();
            question.setContent(rawRefinedDraft);
            question.setUserDirective(directive);
            questionRepository.save(question);
            sendSse(emitter, "draft_intermediate", rawRefinedDraft);
            if (!refinedDraft.equals(rawRefinedDraft)) {
                sendSse(emitter, "washed_intermediate", refinedDraft);
            }

            paceProcessing();
            sendSse(emitter, "progress", "🧼 문장을 더 사람답게 다듬기 위해 표현을 한 번 더 정리하고 있어요.");
            String translatedEn = translationService.translateToEnglish(refinedDraft);

            paceProcessing();
            sendSse(emitter, "progress", "🔁 어색한 느낌이 남지 않도록 한국어 문장을 자연스럽게 다시 다듬고 있어요.");
            String washedKr = prepareDraftForTranslation(
                    normalizeTitleSpacing(translationService.translateToKorean(translatedEn)),
                    maxLength
            );

            question = questionRepository.findById(questionId).orElseThrow();
            question.setWashedKr(washedKr);
            questionRepository.save(question);
            sendSse(emitter, "washed_intermediate", washedKr);

            paceProcessing();
            sendSse(emitter, "progress", "🔎 마지막으로 표현이 어색한 곳이나 의미가 흐려진 부분이 없는지 꼼꼼히 확인하고 있어요.");
            int maxLengthPatch = initialQuestion.getMaxLength();
            int findingTarget = calculateFindingTarget(washedKr);
            DraftAnalysisResult analysis = analyzePatchSafely(
                    emitter,
                    refinedDraft,
                    washedKr,
                    maxLengthPatch,
                    findingTarget,
                    context
            );

            paceProcessing();
            normalizeAnalysis(analysis, refinedDraft, washedKr, findingTarget);

            question = questionRepository.findById(questionId).orElseThrow();
            question.setMistranslations(objectMapper.writeValueAsString(analysis.getMistranslations()));
            question.setAiReview(objectMapper.writeValueAsString(analysis.getAiReviewReport()));

            String responseDraft = (analysis.getHumanPatchedText() != null && !analysis.getHumanPatchedText().isBlank())
                    ? analysis.getHumanPatchedText()
                    : washedKr;
            responseDraft = prepareDraftForTranslation(normalizeTitleSpacing(responseDraft), maxLength);

            Map<String, Object> result = new HashMap<>();
            result.put("draft", washedKr);
            result.put("humanPatched", responseDraft);
            result.put("mistranslations", analysis.getMistranslations());
            result.put("aiReviewReport", analysis.getAiReviewReport());

            sendSse(emitter, "complete", result);
            emitter.complete();
        } catch (SseConnectionClosedException e) {
            log.info("Refinement stream closed by client");
        } catch (Exception e) {
            log.error("Refinement process failed", e);
            try {
                sendSse(emitter, "error", "오류가 발생했습니다.");
            } catch (SseConnectionClosedException ignored) {
                log.info("Refinement stream already closed while reporting error");
            }
            completeQuietly(emitter);
        } finally {
            heartbeat.stop();
        }
    }

    @Transactional(readOnly = true)
    public void processHumanPatch(Long questionId, boolean useDirective, SseEmitter emitter) {
        HeartbeatHandle heartbeat = startHeartbeat(emitter);
        try {
            WorkspaceQuestion initialQuestion = questionRepository.findById(questionId)
                    .orElseThrow(() -> new RuntimeException("Question not found: " + questionId));

            String company = initialQuestion.getApplication().getCompanyName();
            String position = initialQuestion.getApplication().getPosition();
            String questionTitle = initialQuestion.getTitle();
            String companyContext = buildApplicationResearchContext(initialQuestion);

            Thread.sleep(100);
            sendComment(emitter, "flush buffer");

            sendSse(emitter, "progress", "🚀 자기소개서 초안 작성을 시작하고 있어요.");
            paceProcessing();

            sendSse(emitter, "progress", "🔍 다른 문항과 내용이 겹치지 않도록 먼저 확인하고 있어요.");
            List<Experience> allExperiences = experienceRepository.findAll();
            String others = buildOthersContext(initialQuestion, questionId, allExperiences);

            sendSse(emitter, "progress", "📚 이 문항에 가장 잘 맞는 경험을 골라 글에 녹일 준비를 하고 있어요.");
            String context = buildFilteredContext(initialQuestion, questionId, allExperiences);

            paceProcessing();
            sendSse(emitter, "progress", "✍️ 경험과 기업 맥락을 바탕으로 초안을 작성하고 있어요.");

            int maxLengthGen = initialQuestion.getMaxLength();
            String directiveForPrompt = useDirective
                    ? augmentDirectiveForPrompt(initialQuestion.getUserDirective(), maxLengthGen)
                    : "없음";
            int[] targetRange = resolveTargetRange(maxLengthGen, directiveForPrompt, 0.88, 0.95);
            WorkspaceDraftAiService.DraftResponse draftResponse = workspaceDraftAiService.generateDraft(
                    company,
                    position,
                    questionTitle,
                    companyContext,
                    maxLengthGen,
                    targetRange[0],
                    targetRange[1],
                    context,
                    others,
                    directiveForPrompt
            );

            String rawDraft = normalizeTitleSpacing(draftResponse.text).trim();
            String draft = prepareDraftForTranslation(rawDraft, maxLengthGen);

            WorkspaceQuestion question = questionRepository.findById(questionId).orElseThrow();
            question.setContent(rawDraft);
            questionRepository.save(question);
            sendSse(emitter, "draft_intermediate", rawDraft);
            if (!draft.equals(rawDraft)) {
                sendSse(emitter, "washed_intermediate", draft);
            }

            paceProcessing();
            sendSse(emitter, "progress", "🧼 더 자연스럽고 사람다운 문장이 되도록 표현을 한 번 더 정리하고 있어요.");
            String translatedEn = translationService.translateToEnglish(draft);

            paceProcessing();
            sendSse(emitter, "progress", "🔁 읽었을 때 부드럽게 느껴지도록 한국어 문장을 다시 다듬고 있어요.");
            String washedKr = prepareDraftForTranslation(
                    normalizeTitleSpacing(translationService.translateToKorean(translatedEn)),
                    maxLengthGen
            );

            question = questionRepository.findById(questionId).orElseThrow();
            question.setWashedKr(washedKr);
            questionRepository.save(question);
            sendSse(emitter, "washed_intermediate", washedKr);

            paceProcessing();
            sendSse(emitter, "progress", "✨ 마지막으로 어색한 표현과 의미 차이를 점검하면서 결과를 마무리하고 있어요.");
            int maxLengthFinal = initialQuestion.getMaxLength();
            int findingTarget = calculateFindingTarget(washedKr);
            DraftAnalysisResult analysis = analyzePatchSafely(
                    emitter,
                    draft,
                    washedKr,
                    maxLengthFinal,
                    findingTarget,
                    context
            );

            paceProcessing();
            normalizeAnalysis(analysis, draft, washedKr, findingTarget);

            question = questionRepository.findById(questionId).orElseThrow();
            question.setMistranslations(objectMapper.writeValueAsString(analysis.getMistranslations()));
            question.setAiReview(objectMapper.writeValueAsString(analysis.getAiReviewReport()));

            String responseDraft = (analysis.getHumanPatchedText() != null && !analysis.getHumanPatchedText().isBlank())
                    ? analysis.getHumanPatchedText()
                    : washedKr;
            responseDraft = prepareDraftForTranslation(normalizeTitleSpacing(responseDraft), maxLengthFinal);

            Map<String, Object> result = new HashMap<>();
            result.put("draft", washedKr);
            result.put("humanPatched", responseDraft);
            result.put("mistranslations", analysis.getMistranslations());
            result.put("aiReviewReport", analysis.getAiReviewReport());

            sendSse(emitter, "complete", result);
            emitter.complete();
        } catch (SseConnectionClosedException e) {
            log.info("Human patch stream closed by client");
        } catch (Exception e) {
            log.error("Human Patch process failed", e);
            try {
                sendSse(emitter, "error", "오류가 발생했습니다.");
            } catch (SseConnectionClosedException ignored) {
                log.info("Human patch stream already closed while reporting error");
            }
            completeQuietly(emitter);
        } finally {
            heartbeat.stop();
        }
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

    private String buildOthersContext(WorkspaceQuestion initialQuestion, Long questionId, List<Experience> allExperiences) {
        return initialQuestion.getApplication().getQuestions().stream()
                .filter(q -> !q.getId().equals(questionId))
                .map(q -> {
                    String content = q.getContent() != null ? q.getContent() : "";
                    String usedProject = allExperiences.stream()
                            .filter(exp -> content.contains(exp.getTitle()))
                            .map(Experience::getTitle)
                            .findFirst()
                            .orElse("알 수 없음");
                    return String.format("[문항: %s | 주내용: %s | 사용된 소재: %s]", q.getTitle(), content, usedProject);
                })
                .collect(Collectors.joining("\n"));
    }

    private int[] resolveTargetRange(int maxLength, String directive, double defaultMinRatio, double defaultMaxRatio) {
        int minTarget = (int) (maxLength * defaultMinRatio);
        int maxTarget = (int) (maxLength * defaultMaxRatio);

        Integer requestedLength = extractRequestedLength(directive);
        if (requestedLength == null || maxLength <= 0) {
            return new int[]{minTarget, maxTarget};
        }

        int cappedTarget = Math.min(requestedLength, maxLength);
        int requestedMin = Math.max(1, Math.min(cappedTarget, cappedTarget - 25));
        int requestedMax = Math.max(requestedMin, cappedTarget);
        return new int[]{requestedMin, requestedMax};
    }

    private String augmentDirectiveForPrompt(String directive, int maxLength) {
        String normalized = directive == null || directive.isBlank() ? "없음" : directive.trim();
        Integer requestedLength = extractRequestedLength(directive);
        if (requestedLength == null) {
            return normalized;
        }

        int cappedTarget = Math.min(requestedLength, maxLength);
        StringBuilder builder = new StringBuilder();
        if (!"없음".equals(normalized)) {
            builder.append(normalized).append("\n");
        }

        builder.append("Length guidance: ")
                .append(cappedTarget)
                .append("자 부근까지 충분히 채울 것. 글자수는 띄어쓰기를 포함해 계산하며, 보이는 모든 문자는 1글자로 센다. 공백, 문장부호, 괄호, 숫자, 영문, 줄바꿈까지 모두 1글자다.");

        int minimumRequired = Math.max(1, Math.min(cappedTarget, cappedTarget - 50));
        builder.append(" 최소 허용 분량은 ")
                .append(minimumRequired)
                .append("자이며, 그보다 짧으면 실패로 간주할 것.");

        if (requestedLength > maxLength) {
            builder.append(" 현재 하드 리밋은 ")
                    .append(maxLength)
                    .append("자이므로 그 한도 안에서 최대한 가깝게 작성할 것.");
        }

        builder.append(" 최종 답변을 내기 전에 스스로 글자수를 다시 세고, 최소 분량보다 짧으면 내용을 보강한 뒤 다시 셀 것.");

        return builder.toString();
    }

    private Integer extractRequestedLength(String directive) {
        if (directive == null || directive.isBlank()) {
            return null;
        }

        Matcher matcher = REQUESTED_LENGTH_PATTERN.matcher(directive);
        Integer requested = null;
        while (matcher.find()) {
            requested = Integer.parseInt(matcher.group(1));
        }
        return requested;
    }

    private String expandToMinimumLength(
            String text,
            int minTargetChars,
            int maxLength,
            String company,
            String position,
            String questionTitle,
            String companyContext,
            String context,
            String others,
            String directive
    ) {
        String normalized = normalizeTitleSpacing(text).trim();
        if (normalized.isBlank() || minTargetChars <= 0 || normalized.length() >= minTargetChars) {
            return normalized;
        }

        String expansionDirective = (directive == null || directive.isBlank() || "없음".equals(directive))
                ? ""
                : directive + "\n";
        expansionDirective += "현재 초안이 너무 짧다. 최소 " + minTargetChars + "자 이상, 가능하면 " + maxLength
                + "자에 가깝게 채워라. 분량을 억지로 늘리지 말고, 핵심 역량 1~2개를 더 구체적인 근거와 역할, 판단, 결과로 풀어 써라.";

        try {
            WorkspaceDraftAiService.DraftResponse expanded = workspaceDraftAiService.refineDraft(
                    company,
                    position,
                    companyContext,
                    normalized,
                    maxLength,
                    minTargetChars,
                    maxLength,
                    context,
                    others,
                    expansionDirective
            );

            String candidate = enforceLengthLimit(
                    enforceAcceptedTitleStyle(
                            normalizeTitleSpacing(expanded.text),
                            company,
                            position,
                            questionTitle,
                            companyContext,
                            context
                    ),
                    maxLength,
                    company,
                    position,
                    companyContext,
                    context,
                    others
            );

            return candidate.length() >= normalized.length() ? candidate : normalized;
        } catch (Exception e) {
            log.warn("Failed to expand under-length draft to minimum target. current={}, min={}", normalized.length(), minTargetChars, e);
            return normalized;
        }
    }

    private String buildApplicationResearchContext(WorkspaceQuestion question) {
        List<String> sections = new ArrayList<>();

        if (question.getApplication().getCompanyResearch() != null && !question.getApplication().getCompanyResearch().isBlank()) {
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

    private String buildFilteredContext(WorkspaceQuestion initialQuestion, Long questionId, List<Experience> allExperiences) {
        Set<Long> excludedExperienceIds = extractUsedExperienceIds(initialQuestion, questionId, allExperiences);
        List<com.resumade.api.workspace.dto.ExperienceContextResponse.ContextItem> selectedContext =
                experienceVectorRetrievalService.search(initialQuestion.getTitle(), 4, excludedExperienceIds);

        if (!selectedContext.isEmpty()) {
            return selectedContext.stream()
                    .map(item -> String.format("[경험: %s]\n%s", item.getExperienceTitle(), item.getRelevantPart()))
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

    private Set<Long> extractUsedExperienceIds(WorkspaceQuestion initialQuestion, Long questionId, List<Experience> allExperiences) {
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
            String others
    ) {
        if (text == null) {
            return null;
        }

        String normalized = normalizeTitleSpacing(text).trim();
        if (maxLength <= 0 || normalized.length() <= maxLength) {
            return normalized;
        }

        log.warn("Draft exceeded max length. current={}, limit={}", normalized.length(), maxLength);

        try {
            WorkspaceDraftAiService.DraftResponse shortened = workspaceDraftAiService.shortenToLimit(
                    company,
                    position,
                    companyContext,
                    normalized,
                    maxLength,
                    context,
                    others
            );

            String candidate = normalizeTitleSpacing(shortened.text).trim();
            if (candidate.length() <= maxLength) {
                return candidate;
            }

            log.warn("Length enforcement retry still exceeded limit. current={}, limit={}", candidate.length(), maxLength);
            return hardTrimToLimit(candidate, maxLength);
        } catch (Exception e) {
            log.warn("Length enforcement AI retry failed. Falling back to hard trim.", e);
            return hardTrimToLimit(normalized, maxLength);
        }
    }

    private String hardTrimToLimit(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }

        String trimmed = text.substring(0, maxLength).trim();
        int breakpoint = Math.max(
                Math.max(trimmed.lastIndexOf("다."), trimmed.lastIndexOf("요.")),
                Math.max(trimmed.lastIndexOf(". "), Math.max(trimmed.lastIndexOf("! "), trimmed.lastIndexOf("? ")))
        );

        if (breakpoint >= Math.max(0, maxLength - 80)) {
            return trimmed.substring(0, breakpoint + 1).trim();
        }

        return trimmed;
    }

    private String prepareDraftForTranslation(String text, int maxLength) {
        if (text == null) {
            return null;
        }

        String normalized = normalizeTitleSpacing(text).trim();
        if (maxLength > 0 && normalized.length() > maxLength) {
            log.warn("Draft exceeded max length. current={}, limit={}", normalized.length(), maxLength);
            return hardTrimToLimit(normalized, maxLength);
        }

        return normalized;
    }

    private DraftAnalysisResult analyzePatchSafely(
            SseEmitter emitter,
            String originalDraft,
            String washedKr,
            int maxLength,
            int findingTarget,
            String context
    ) {
        try {
            return workspacePatchAiService.analyzePatch(
                    originalDraft,
                    washedKr,
                    maxLength,
                    (int) (maxLength * 0.92),
                    findingTarget,
                    context
            );
        } catch (Exception e) {
            if (isQuotaError(e)) {
                log.warn("Patch analysis skipped due to OpenAI quota exhaustion");
                sendSse(emitter, "progress", "⚠️ 오역 검토는 잠시 건너뛰고, 작성된 결과를 먼저 보여드릴게요.");
                return DraftAnalysisResult.builder()
                        .humanPatchedText(washedKr)
                        .mistranslations(new ArrayList<>())
                        .aiReviewReport(DraftAnalysisResult.AiReviewReport.builder()
                                .summary("오역 검토 모델 사용량이 초과되어 이번에는 세탁 결과만 먼저 제공했습니다.")
                                .build())
                        .build();
            }

            throw e;
        }
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

    private String enforceAcceptedTitleStyle(
            String text,
            String company,
            String position,
            String question,
            String companyContext,
            String context
    ) {
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
                    context
            );

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
        return lowered.contains("지원 동기")
                || lowered.contains("입사 후")
                || lowered.contains("목표")
                || lowered.contains("포부")
                || lowered.contains("역량")
                || lowered.contains("성장과정")
                || lowered.contains("및")
                || title.length() > 18;
    }

    private void normalizeAnalysis(DraftAnalysisResult analysis, String originalDraft, String washedKr, int findingTarget) {
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
                if (highlightSpan != null && isReasonableHighlightSpan(washedKr, highlightSpan.start(), highlightSpan.end())) {
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
            return 4;
        }

        return Math.max(4, Math.min(10, (washedKr.length() / 220) + 3));
    }

    private void supplementMistranslations(
            DraftAnalysisResult analysis,
            String originalDraft,
            String washedKr,
            int findingTarget
    ) {
        if (analysis == null || washedKr == null || washedKr.isBlank()) {
            return;
        }

        List<DraftAnalysisResult.Mistranslation> mistranslations = analysis.getMistranslations();
        if (mistranslations == null) {
            mistranslations = new ArrayList<>();
            analysis.setMistranslations(mistranslations);
        }

        // Do not auto-add sentence-level fallback findings.
        // Broad sentence highlights look noisy in the UI and often obscure the real phrase-level issue.
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

    private boolean containsTranslatedSpan(List<DraftAnalysisResult.Mistranslation> mistranslations, String translated) {
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
                .map(token -> token.replaceAll("[^0-9a-zA-Z가-힣]+", ""))
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

        List<Integer> exactMatches = findExactMatchIndexes(source, translated);
        if (exactMatches.size() == 1) {
            int start = exactMatches.get(0);
            return new HighlightSpan(start, start + translated.length());
        }

        if (!exactMatches.isEmpty()) {
            return null;
        }

        int compactIndex = findTranslatedSpan(source, translated);
        if (compactIndex < 0) {
            return null;
        }

        return new HighlightSpan(compactIndex, compactIndex + translated.length());
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

    private boolean isReasonableHighlightSpan(String source, int start, int end) {
        if (source == null || start < 0 || end <= start || end > source.length()) {
            return false;
        }

        String span = source.substring(start, end).trim();
        if (span.isBlank()) {
            return false;
        }

        if (span.length() > 36) {
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

    private int findTranslatedSpan(String source, String target) {
        if (source == null || target == null || target.isEmpty()) {
            return -1;
        }

        int directIndex = source.indexOf(target);
        if (directIndex >= 0) {
            return directIndex;
        }

        String compactSource = source.replaceAll("\\s+", "");
        String compactTarget = target.replaceAll("\\s+", "");
        int compactIndex = compactSource.indexOf(compactTarget);
        if (compactIndex < 0) {
            return -1;
        }

        int count = 0;
        for (int i = 0; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (!Character.isWhitespace(ch)) {
                if (count == compactIndex) {
                    return i;
                }
                count++;
            }
        }

        return -1;
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
            log.info("📡 [SSE Send] Name: {}, Data: {}", name, sseData);
            emitter.send(SseEmitter.event().name(name).data(sseData));
        } catch (IOException | IllegalStateException e) {
            log.warn("❌ Failed to send SSE event: {}", name);
            throw new SseConnectionClosedException(e);
        }
    }

    private void sendComment(SseEmitter emitter, String comment) {
        try {
            emitter.send(SseEmitter.event().comment(comment));
        } catch (IOException | IllegalStateException e) {
            log.warn("❌ Failed to send SSE comment");
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
}
