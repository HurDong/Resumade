package com.resumade.api.workspace.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.infra.sse.Utf8SseSupport;
import com.resumade.api.workspace.domain.Application;
import com.resumade.api.workspace.domain.ApplicationRepository;
import com.resumade.api.workspace.domain.CompanyFitProfile;
import com.resumade.api.workspace.domain.CompanyFitProfileRepository;
import com.resumade.api.workspace.domain.QuestionStrategyCard;
import com.resumade.api.workspace.domain.QuestionStrategyCardRepository;
import com.resumade.api.workspace.domain.WorkspaceQuestion;
import com.resumade.api.workspace.domain.WorkspaceQuestionRepository;
import com.resumade.api.workspace.dto.BatchPlanRequest;
import com.resumade.api.workspace.dto.BatchPlanResponse;
import com.resumade.api.workspace.dto.CompanyFitProfileDto;
import com.resumade.api.workspace.dto.QuestionStrategyCardActivateRequest;
import com.resumade.api.workspace.dto.QuestionStrategyCardBatchResponse;
import com.resumade.api.workspace.dto.QuestionStrategyCardCandidateResponse;
import com.resumade.api.workspace.dto.QuestionStrategyCardDto;
import com.resumade.api.workspace.dto.QuestionStrategyCardResponse;
import com.resumade.api.workspace.dto.QuestionStrategyCardReviewNoteRequest;
import com.resumade.api.workspace.prompt.QuestionCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionStrategyCardService {

    private static final Duration CANDIDATE_TTL = Duration.ofMinutes(30);
    private static final String SOURCE_SINGLE = "SINGLE";
    private static final String SOURCE_BATCH = "BATCH";

    private final ApplicationRepository applicationRepository;
    private final WorkspaceQuestionRepository questionRepository;
    private final CompanyFitProfileRepository companyFitProfileRepository;
    private final QuestionStrategyCardRepository strategyCardRepository;
    private final WorkspaceBatchPlanService workspaceBatchPlanService;
    private final ObjectMapper objectMapper;

    private final Map<String, StrategyCardJob> jobCache = new ConcurrentHashMap<>();
    private final Map<String, StrategyCardCandidate> candidateCache = new ConcurrentHashMap<>();

    public String initSingle(Long questionId) {
        WorkspaceQuestion question = questionRepository.findByIdWithApplication(questionId)
                .orElseThrow(() -> new IllegalArgumentException("Question not found: " + questionId));
        Long applicationId = question.getApplication().getId();

        BatchPlanRequest request = new BatchPlanRequest();
        request.setApplicationId(applicationId);
        request.setQuestions(List.of(toSnapshot(question)));

        String uuid = UUID.randomUUID().toString();
        jobCache.put(uuid, new StrategyCardJob(applicationId, request, SOURCE_SINGLE));
        cleanupExpiredCandidates();
        log.info("Initialized single strategy card questionId={} uuid={}", questionId, uuid);
        return uuid;
    }

    public String initBatch(BatchPlanRequest request) {
        if (request == null || request.getApplicationId() == null) {
            throw new IllegalArgumentException("applicationId is required.");
        }
        applicationRepository.findById(request.getApplicationId())
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + request.getApplicationId()));
        if (request.getQuestions() == null || request.getQuestions().isEmpty()) {
            throw new IllegalArgumentException("questions are required.");
        }

        String uuid = UUID.randomUUID().toString();
        jobCache.put(uuid, new StrategyCardJob(request.getApplicationId(), request, SOURCE_BATCH));
        cleanupExpiredCandidates();
        log.info("Initialized batch strategy cards applicationId={} questionCount={} uuid={}",
                request.getApplicationId(), request.getQuestions().size(), uuid);
        return uuid;
    }

    public void processCards(String uuid, SseEmitter emitter) {
        StrategyCardJob job = jobCache.remove(uuid);
        if (job == null) {
            sendError(emitter, "Strategy card session expired. Please generate it again.");
            return;
        }

        try {
            sendEvent(emitter, "START", "전략카드 생성을 시작합니다.");
            sendEvent(emitter, "PLANNING", "문항 의도, 경험 근거, 회사 Fit 연결점을 설계하고 있습니다.");

            Application application = applicationRepository.findById(job.applicationId())
                    .orElseThrow(() -> new IllegalArgumentException("Application not found: " + job.applicationId()));
            ActiveFitProfile activeFitProfile = readActiveFitProfile(job.applicationId());

            BatchPlanResponse plan = workspaceBatchPlanService.createPlan(job.request());
            Map<Long, BatchPlanRequest.QuestionSnapshot> questionMap = job.request().getQuestions().stream()
                    .filter(Objects::nonNull)
                    .filter(question -> question.getQuestionId() != null)
                    .collect(Collectors.toMap(
                            BatchPlanRequest.QuestionSnapshot::getQuestionId,
                            question -> question,
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));

            sendEvent(emitter, "STRUCTURING", "초안 생성에 주입할 전략 지시문과 미리보기 카드를 정리하고 있습니다.");

            List<StrategyCardCandidateItem> cards = new ArrayList<>();
            for (BatchPlanResponse.Assignment assignment : plan.getAssignments()) {
                BatchPlanRequest.QuestionSnapshot question = questionMap.get(assignment.getQuestionId());
                if (question == null) {
                    continue;
                }
                QuestionStrategyCardDto card = toCard(application, question, assignment, activeFitProfile);
                cards.add(new StrategyCardCandidateItem(
                        assignment.getQuestionId(),
                        card,
                        card.getDraftDirective(),
                        activeFitProfile.id()
                ));
            }

            if (cards.isEmpty()) {
                throw new IllegalStateException("No strategy card was generated.");
            }

            StrategyCardCandidate candidate = new StrategyCardCandidate(
                    uuid,
                    job.applicationId(),
                    job.sourceType(),
                    firstNonBlank(plan.getModel(), "workspace-plan"),
                    Instant.now().plus(CANDIDATE_TTL),
                    cards
            );
            candidateCache.put(uuid, candidate);

            sendEvent(emitter, "COMPLETE", toCandidateResponse(candidate));
            emitter.complete();
        } catch (Exception e) {
            log.error("Strategy card generation failed uuid={} reason={}", uuid, e.getMessage(), e);
            sendError(emitter, e.getMessage());
        }
    }

    @Transactional
    public QuestionStrategyCardResponse activateSingle(Long questionId, QuestionStrategyCardActivateRequest request) {
        StrategyCardCandidate candidate = resolveCandidate(request);
        StrategyCardCandidateItem item = candidate.cards().stream()
                .filter(card -> questionId.equals(card.questionId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Strategy card candidate does not belong to this question."));

        QuestionStrategyCardResponse response = saveActiveCard(item, SOURCE_SINGLE, candidate.modelName());
        candidateCache.remove(request.uuid());
        return response;
    }

    @Transactional
    public QuestionStrategyCardBatchResponse activateBatch(QuestionStrategyCardActivateRequest request) {
        StrategyCardCandidate candidate = resolveCandidate(request);
        if (!SOURCE_BATCH.equals(candidate.sourceType())) {
            throw new IllegalArgumentException("Strategy card candidate is not a batch candidate.");
        }

        List<QuestionStrategyCardResponse> responses = candidate.cards().stream()
                .map(item -> saveActiveCard(item, SOURCE_BATCH, candidate.modelName()))
                .toList();
        candidateCache.remove(request.uuid());

        return QuestionStrategyCardBatchResponse.builder()
                .applicationId(candidate.applicationId())
                .sourceType(SOURCE_BATCH)
                .modelName(candidate.modelName())
                .cards(responses)
                .build();
    }

    public void discardCandidate(Long questionId, String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return;
        }
        StrategyCardCandidate candidate = candidateCache.get(uuid);
        if (candidate != null && candidate.cards().stream().anyMatch(card -> questionId.equals(card.questionId()))) {
            candidateCache.remove(uuid);
        }
    }

    public void discardBatchCandidate(Long applicationId, String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return;
        }
        StrategyCardCandidate candidate = candidateCache.get(uuid);
        if (candidate != null && applicationId.equals(candidate.applicationId())) {
            candidateCache.remove(uuid);
        }
    }

    @Transactional(readOnly = true)
    public Optional<QuestionStrategyCardResponse> getActiveCard(Long questionId) {
        return strategyCardRepository.findByQuestionId(questionId)
                .map(this::toResponse);
    }

    public ResponseEntity<QuestionStrategyCardResponse> responseOrNoContent(Long questionId) {
        return getActiveCard(questionId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Transactional
    public QuestionStrategyCardResponse updateReviewNote(Long questionId, QuestionStrategyCardReviewNoteRequest request) {
        QuestionStrategyCard entity = strategyCardRepository.findByQuestionId(questionId)
                .orElseThrow(() -> new IllegalStateException("No active strategy card exists for this question."));

        entity.setReviewNote(request == null ? null : request.reviewNote());
        return toResponse(entity);
    }

    private QuestionStrategyCardResponse saveActiveCard(
            StrategyCardCandidateItem item,
            String sourceType,
            String modelName
    ) {
        WorkspaceQuestion question = questionRepository.findByIdWithApplication(item.questionId())
                .orElseThrow(() -> new IllegalArgumentException("Question not found: " + item.questionId()));

        QuestionStrategyCard entity = strategyCardRepository.findByQuestionId(item.questionId())
                .orElseGet(() -> new QuestionStrategyCard(question));

        entity.setQuestion(question);
        entity.setApplication(question.getApplication());
        entity.setCardJson(writeCardJson(item.card()));
        entity.setDirectivePrefix(item.directivePrefix());
        entity.setReviewNote(null);
        entity.setSourceType(sourceType);
        entity.setModelName(modelName);
        entity.setFitProfileId(item.fitProfileId());

        question.setBatchStrategyDirective(item.directivePrefix());
        questionRepository.save(question);
        return toResponse(strategyCardRepository.save(entity));
    }

    private StrategyCardCandidate resolveCandidate(QuestionStrategyCardActivateRequest request) {
        if (request == null || request.uuid() == null || request.uuid().isBlank()) {
            throw new IllegalArgumentException("uuid is required.");
        }
        cleanupExpiredCandidates();
        StrategyCardCandidate candidate = candidateCache.get(request.uuid());
        if (candidate == null) {
            throw new IllegalStateException("Strategy card candidate expired. Please generate it again.");
        }
        return candidate;
    }

    private BatchPlanRequest.QuestionSnapshot toSnapshot(WorkspaceQuestion question) {
        BatchPlanRequest.QuestionSnapshot snapshot = new BatchPlanRequest.QuestionSnapshot();
        snapshot.setQuestionId(question.getId());
        snapshot.setTitle(question.getTitle());
        snapshot.setMaxLength(question.getMaxLength());
        snapshot.setUserDirective(question.getUserDirective());
        snapshot.setBatchStrategyDirective("");
        snapshot.setContent(question.getContent());
        snapshot.setWashedKr(question.getWashedKr());
        snapshot.setCategory(question.getCategory());
        return snapshot;
    }

    private QuestionStrategyCardDto toCard(
            Application application,
            BatchPlanRequest.QuestionSnapshot question,
            BatchPlanResponse.Assignment assignment,
            ActiveFitProfile activeFitProfile
    ) {
        CompanyFitProfileDto fitProfile = activeFitProfile.profile();
        List<String> lexicon = fitProfile == null || fitProfile.getDomainLexicon() == null
                ? List.of()
                : fitProfile.getDomainLexicon().stream()
                        .map(CompanyFitProfileDto.LexiconItem::getTerm)
                        .filter(value -> value != null && !value.isBlank())
                        .distinct()
                        .limit(5)
                        .toList();
        List<String> evidence = fitProfile == null || fitProfile.getEvidence() == null
                ? List.of()
                : fitProfile.getEvidence().stream()
                        .map(item -> firstNonBlank(item.getTitle(), item.getUri()))
                        .filter(value -> value != null && !value.isBlank())
                        .distinct()
                        .limit(4)
                        .toList();

        String companyAnchor = fitProfile == null
                ? firstNonBlank(application.getCompanyResearch(), application.getAiInsight(), application.getRawJd())
                : firstNonBlank(fitProfile.getSummary(), application.getCompanyResearch(), application.getAiInsight());

        QuestionCategory category = assignment.getCategory() != null
                ? assignment.getCategory()
                : resolveCategory(question.getCategory());

        List<String> warnings = new ArrayList<>(safeList(assignment.getAvoidDetails()));
        if (fitProfile != null && fitProfile.getStrategyWarnings() != null) {
            fitProfile.getStrategyWarnings().stream()
                    .map(item -> firstNonBlank(item.getTitle(), item.getDetail()))
                    .filter(value -> value != null && !value.isBlank())
                    .limit(3)
                    .forEach(warnings::add);
        }

        List<String> confidenceNotes = new ArrayList<>();
        if (fitProfile == null) {
            confidenceNotes.add("등록된 기업 Fit 프로필이 없어 JD/기업분석/RAG 기반으로 전략을 구성했습니다.");
        }
        if (safeList(assignment.getPrimaryExperiences()).isEmpty()
                || safeList(assignment.getPrimaryExperiences()).stream().anyMatch(value -> value.contains("필요"))) {
            confidenceNotes.add("검증 가능한 경험 근거가 약합니다. 초안 생성 전 경험 보관소 연결을 확인하세요.");
        }
        if (assignment.getReasoning() != null && assignment.getReasoning().toLowerCase().contains("fallback")) {
            confidenceNotes.add("AI 전략 생성이 불안정해 휴리스틱 fallback 전략이 사용되었습니다.");
        }

        String directive = firstNonBlank(assignment.getDirectivePrefix(), buildFallbackDirective(assignment));

        return QuestionStrategyCardDto.builder()
                .questionId(question.getQuestionId())
                .questionTitle(question.getTitle())
                .category(category.name())
                .summary(firstNonBlank(assignment.getAngle(), "이 문항은 검증 가능한 경험 근거와 회사 Fit 연결을 함께 보여주는 방향으로 작성합니다."))
                .intent(QuestionStrategyCardDto.StrategyText.builder()
                        .title(firstNonBlank(assignment.getQuestionIntentTag(), category.name()))
                        .detail(firstNonBlank(assignment.getIntentRationale(), "문항의 평가 의도를 먼저 맞춘 뒤 경험 근거를 배치합니다."))
                        .build())
                .primaryClaim(firstNonBlank(assignment.getAngle(), assignment.getDomainBridge(), "검증 가능한 경험으로 직무 적합성을 증명합니다."))
                .experiencePlan(QuestionStrategyCardDto.ExperiencePlan.builder()
                        .primaryExperiences(safeList(assignment.getPrimaryExperiences()))
                        .facets(safeList(assignment.getExperienceFacets()))
                        .proofPoints(safeList(assignment.getFocusDetails()))
                        .learningPoints(safeList(assignment.getLearningPoints()))
                        .build())
                .fitConnection(QuestionStrategyCardDto.FitConnection.builder()
                        .companyAnchor(snippet(companyAnchor, 260))
                        .domainBridge(firstNonBlank(assignment.getDomainBridge(), "JD와 기업 분석에서 확인된 직무 문제와 경험 근거를 연결합니다."))
                        .lexicon(lexicon)
                        .evidence(evidence)
                        .build())
                .paragraphPlan(buildParagraphPlan(question, assignment, category))
                .warnings(warnings.stream().filter(value -> value != null && !value.isBlank()).distinct().limit(6).toList())
                .draftDirective(directive)
                .confidenceNotes(confidenceNotes)
                .build();
    }

    private List<QuestionStrategyCardDto.ParagraphPlan> buildParagraphPlan(
            BatchPlanRequest.QuestionSnapshot question,
            BatchPlanResponse.Assignment assignment,
            QuestionCategory category
    ) {
        int maxLength = question.getMaxLength() == null ? 800 : question.getMaxLength();
        List<QuestionStrategyCardDto.ParagraphPlan> paragraphs = new ArrayList<>();
        paragraphs.add(QuestionStrategyCardDto.ParagraphPlan.builder()
                .paragraph(1)
                .role("문항 의도에 맞는 핵심 주장과 상황 제시")
                .contents(nonBlankList(assignment.getAngle(), assignment.getIntentRationale()))
                .build());
        paragraphs.add(QuestionStrategyCardDto.ParagraphPlan.builder()
                .paragraph(2)
                .role(category == QuestionCategory.TREND_INSIGHT
                        ? "트렌드 판단 근거와 회사 적용 장면"
                        : "검증 가능한 경험 facet, 행동, 결과 전개")
                .contents(joinedList(assignment.getExperienceFacets(), assignment.getFocusDetails()))
                .build());
        if (maxLength > 650) {
            paragraphs.add(QuestionStrategyCardDto.ParagraphPlan.builder()
                    .paragraph(3)
                    .role("회사 Fit 연결과 중복 방지된 마무리")
                    .contents(joinedList(nonBlankList(assignment.getDomainBridge()), assignment.getLearningPoints()))
                    .build());
        }
        return paragraphs;
    }

    private String buildFallbackDirective(BatchPlanResponse.Assignment assignment) {
        List<String> lines = new ArrayList<>();
        lines.add("[Strategy Card]");
        lines.add("Question intent: " + firstNonBlank(assignment.getQuestionIntentTag(), "DEFAULT"));
        if (!safeList(assignment.getPrimaryExperiences()).isEmpty()) {
            lines.add("Primary experience: " + String.join(", ", safeList(assignment.getPrimaryExperiences())));
        }
        if (!safeList(assignment.getExperienceFacets()).isEmpty()) {
            lines.add("Use this specific facet: " + String.join(" | ", safeList(assignment.getExperienceFacets())));
        }
        if (assignment.getDomainBridge() != null && !assignment.getDomainBridge().isBlank()) {
            lines.add("Domain bridge: " + assignment.getDomainBridge().trim());
        }
        if (!safeList(assignment.getAvoidDetails()).isEmpty()) {
            lines.add("Avoid: " + String.join(" | ", safeList(assignment.getAvoidDetails())));
        }
        lines.add("[/Strategy Card]");
        return String.join("\n", lines);
    }

    private ActiveFitProfile readActiveFitProfile(Long applicationId) {
        Optional<CompanyFitProfile> entity = companyFitProfileRepository.findByApplicationId(applicationId);
        if (entity.isEmpty()) {
            return new ActiveFitProfile(null, null);
        }
        try {
            return new ActiveFitProfile(
                    entity.get().getId(),
                    objectMapper.readValue(entity.get().getProfileJson(), CompanyFitProfileDto.class)
            );
        } catch (IOException e) {
            log.warn("Failed to parse company fit profile for strategy card applicationId={}: {}",
                    applicationId, e.getMessage());
            return new ActiveFitProfile(entity.get().getId(), null);
        }
    }

    private QuestionStrategyCardCandidateResponse toCandidateResponse(StrategyCardCandidate candidate) {
        return QuestionStrategyCardCandidateResponse.builder()
                .uuid(candidate.uuid())
                .applicationId(candidate.applicationId())
                .sourceType(candidate.sourceType())
                .modelName(candidate.modelName())
                .expiresAt(candidate.expiresAt())
                .cards(candidate.cards().stream()
                        .map(item -> toCandidateCardResponse(candidate, item))
                        .toList())
                .build();
    }

    private QuestionStrategyCardResponse toCandidateCardResponse(
            StrategyCardCandidate candidate,
            StrategyCardCandidateItem item
    ) {
        return QuestionStrategyCardResponse.builder()
                .id(null)
                .applicationId(candidate.applicationId())
                .questionId(item.questionId())
                .card(item.card())
                .directivePrefix(item.directivePrefix())
                .reviewNote(null)
                .sourceType(candidate.sourceType())
                .modelName(candidate.modelName())
                .fitProfileId(item.fitProfileId())
                .createdAt(null)
                .updatedAt(null)
                .build();
    }

    private QuestionStrategyCardResponse toResponse(QuestionStrategyCard entity) {
        return QuestionStrategyCardResponse.builder()
                .id(entity.getId())
                .applicationId(entity.getApplication().getId())
                .questionId(entity.getQuestion().getId())
                .card(readCardJson(entity.getCardJson()))
                .directivePrefix(entity.getDirectivePrefix())
                .reviewNote(entity.getReviewNote())
                .sourceType(entity.getSourceType())
                .modelName(entity.getModelName())
                .fitProfileId(entity.getFitProfileId())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private String writeCardJson(QuestionStrategyCardDto card) {
        try {
            return objectMapper.writeValueAsString(card);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize strategy card.", e);
        }
    }

    private QuestionStrategyCardDto readCardJson(String json) {
        try {
            return objectMapper.readValue(json, QuestionStrategyCardDto.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse stored strategy card.", e);
        }
    }

    private void cleanupExpiredCandidates() {
        Instant now = Instant.now();
        candidateCache.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            if (data instanceof String text) {
                emitter.send(Utf8SseSupport.textEvent(name, text));
            } else {
                emitter.send(Utf8SseSupport.jsonEvent(name, data));
            }
        } catch (IOException | IllegalStateException e) {
            log.warn("Failed to send strategy card SSE event: {}", e.getMessage());
        }
    }

    private void sendError(SseEmitter emitter, String message) {
        try {
            emitter.send(Utf8SseSupport.jsonEvent("ERROR", Map.of("message", message)));
            emitter.complete();
        } catch (IOException | IllegalStateException e) {
            log.warn("Failed to send strategy card error event: {}", e.getMessage());
        }
    }

    private QuestionCategory resolveCategory(QuestionCategory category) {
        return category == null ? QuestionCategory.DEFAULT : category;
    }

    private List<String> safeList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(5)
                .toList();
    }

    private List<String> nonBlankList(String... values) {
        if (values == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                result.add(value.trim());
            }
        }
        return result;
    }

    private List<String> joinedList(List<String> first, List<String> second) {
        List<String> result = new ArrayList<>();
        result.addAll(safeList(first));
        result.addAll(safeList(second));
        return result.stream().distinct().limit(5).toList();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String snippet(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private record StrategyCardJob(Long applicationId, BatchPlanRequest request, String sourceType) {
    }

    private record StrategyCardCandidate(
            String uuid,
            Long applicationId,
            String sourceType,
            String modelName,
            Instant expiresAt,
            List<StrategyCardCandidateItem> cards
    ) {
    }

    private record StrategyCardCandidateItem(
            Long questionId,
            QuestionStrategyCardDto card,
            String directivePrefix,
            Long fitProfileId
    ) {
    }

    private record ActiveFitProfile(Long id, CompanyFitProfileDto profile) {
    }
}
