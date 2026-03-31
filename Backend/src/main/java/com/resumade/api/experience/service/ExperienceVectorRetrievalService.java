package com.resumade.api.experience.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.resumade.api.experience.document.ExperienceDocument;
import com.resumade.api.experience.document.ExperienceDocumentRepository;
import com.resumade.api.experience.domain.Experience;
import com.resumade.api.experience.domain.ExperienceRepository;
import com.resumade.api.workspace.dto.ExperienceContextResponse;
import com.resumade.api.workspace.prompt.QuestionCategory;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExperienceVectorRetrievalService {

    private static final String INDEX_NAME = "experience-docs";
    private static final int DEFAULT_LIMIT = 3;
    private static final int MAX_SNIPPET_LENGTH = 400;
    private static final int MAX_QUERY_VARIANTS = 4;
    private static final int VECTOR_FETCH_MULTIPLIER = 4;

    // -------------------------------------------------------------------------
    // 카테고리별 필드 부스팅 가중치 (기본값에 더해지는 추가 점수)
    //   기본 가중치: title(36) > description(24) > techStack(22) > role(18) >
    //               metrics(16) > rawContent(16) > category(10) > fileName(8)
    // -------------------------------------------------------------------------
    private static final Map<QuestionCategory, CategoryFieldBoost> CATEGORY_BOOSTS = Map.of(
            // 지원동기: 서사/맥락 중심 — description, rawContent 강화
            QuestionCategory.MOTIVATION,       new CategoryFieldBoost(0,  10, 0,   0,  0,   8, 0),
            // 직무경험: 기술/정량 중심 — techStack, metrics, role 강화
            QuestionCategory.EXPERIENCE,       new CategoryFieldBoost(0,   0, 14, 10,  8,   0, 0),
            // 문제해결: 문제-해결 서사 중심 — description, rawContent 강화
            QuestionCategory.PROBLEM_SOLVING,  new CategoryFieldBoost(0,  12, 0,   4,  0,  10, 0),
            // 협업/리더십: 팀/역할 맥락 중심 — role, description 강화
            QuestionCategory.COLLABORATION,    new CategoryFieldBoost(0,   8, 0,   0, 12,   4, 0),
            // 성장: CS/딥다이브형 학습 — description, techStack, rawContent 강화
            QuestionCategory.GROWTH,           new CategoryFieldBoost(0,  10, 8,   2,  0,  10, 0),
            // 조직문화 적합성: 실행·검증·오너십 — metrics, description, role 강화
            QuestionCategory.CULTURE_FIT,      new CategoryFieldBoost(0,   8, 4,  12,  6,   8, 0),
            // 기술/산업 인사이트: 도메인 키워드와 관련 경험 맥락 강조
            QuestionCategory.TREND_INSIGHT,    new CategoryFieldBoost(4,  10, 8,   4,  0,  10, 0),
            // 기본: 부스팅 없음
            QuestionCategory.DEFAULT,          new CategoryFieldBoost(0,   0, 0,   0,  0,   0, 0)
    );

    /**
     * 카테고리별 필드 가중치 추가분을 담는 Value Object.
     *
     * @param titleBoost        경험 제목 추가 가중치
     * @param descriptionBoost  경험 설명 추가 가중치
     * @param techStackBoost    기술 스택 추가 가중치
     * @param metricsBoost      성과 지표 추가 가중치
     * @param roleBoost         역할 추가 가중치
     * @param rawContentBoost   원문 콘텐츠 추가 가중치
     * @param categoryBoost     카테고리명 추가 가중치
     */
    private record CategoryFieldBoost(
            double titleBoost,
            double descriptionBoost,
            double techStackBoost,
            double metricsBoost,
            double roleBoost,
            double rawContentBoost,
            double categoryBoost
    ) {
        static CategoryFieldBoost none() {
            return new CategoryFieldBoost(0, 0, 0, 0, 0, 0, 0);
        }
    }
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{IsAlphabetic}\\p{IsDigit}\\u3131-\\u318E\\uAC00-\\uD7A3+#._-]+");
    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "for", "with", "from", "that", "this", "your", "into", "self", "intro", "introduction",
            "resume", "question", "company", "position", "role", "experience", "project", "draft", "paragraph",
            "focus", "tone", "keep", "avoid", "more", "detail", "details", "specific", "specificity", "structure",
            "문항", "질문", "답변", "자소서", "자기소개서", "지원", "회사", "기업", "직무", "경험", "프로젝트", "역량", "강점",
            "성과", "협업", "내용", "문단", "구조", "수정", "보완", "추가", "요청", "디테일", "구체", "구체화", "강조", "중심",
            "기반", "통해", "위해", "대한", "관련", "있는", "있습니다", "입니다", "했다", "했습니다", "과정", "부분", "사례"
    );

    private final ExperienceRepository experienceRepository;
    private final ExperienceDocumentRepository documentRepository;
    private final ElasticsearchClient elasticsearchClient;

    @Value("${openai.api.key:demo}")
    private String openAiApiKey;

    private volatile EmbeddingModel embeddingModel;

    @Transactional(readOnly = true)
    public List<ExperienceContextResponse.ContextItem> search(String query, int limit) {
        return search(query, limit, Collections.emptySet(), Collections.emptyList(), QuestionCategory.DEFAULT);
    }

    @Transactional(readOnly = true)
    public List<ExperienceContextResponse.ContextItem> search(String query, int limit, Set<Long> excludedExperienceIds) {
        return search(query, limit, excludedExperienceIds, Collections.emptyList(), QuestionCategory.DEFAULT);
    }

    @Transactional(readOnly = true)
    public List<ExperienceContextResponse.ContextItem> search(
            String query,
            int limit,
            Set<Long> excludedExperienceIds,
            List<String> supportingQueries
    ) {
        return search(query, limit, excludedExperienceIds, supportingQueries, QuestionCategory.DEFAULT);
    }

    /**
     * 카테고리 인식 하이브리드 검색 (메인 오버로드).
     *
     * <p>{@link QuestionCategory}에 따라 키워드 검색의 필드별 가중치를 동적으로 조정합니다.
     * 예) EXPERIENCE 카테고리 → techStack, metrics, role 필드에 추가 가중치 부여
     *
     * @param category 분류기가 판별한 문항 카테고리 — null이면 DEFAULT로 처리
     */
    @Transactional(readOnly = true)
    public List<ExperienceContextResponse.ContextItem> search(
            String query,
            int limit,
            Set<Long> excludedExperienceIds,
            List<String> supportingQueries,
            QuestionCategory category
    ) {
        QuestionCategory effectiveCategory = (category != null) ? category : QuestionCategory.DEFAULT;
        CategoryFieldBoost boost = CATEGORY_BOOSTS.getOrDefault(effectiveCategory, CategoryFieldBoost.none());

        log.debug("ExperienceVectorRetrievalService: search category={} query=\"{}\"",
                effectiveCategory, query != null ? query.substring(0, Math.min(query.length(), 60)) : "");

        List<WeightedQuery> weightedQueries = buildWeightedQueries(query, supportingQueries);
        if (weightedQueries.isEmpty()) {
            return Collections.emptyList();
        }

        int effectiveLimit = Math.max(limit, DEFAULT_LIMIT);
        Map<Long, Experience> experienceMap = loadAvailableExperiences(excludedExperienceIds);
        if (experienceMap.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, RankedCandidate> ranked = new LinkedHashMap<>();

        if (hasIndexedDocuments()) {
            for (WeightedQuery weightedQuery : weightedQueries) {
                float[] vector = computeEmbedding(weightedQuery.text());
                if (vector == null) {
                    continue;
                }

                try {
                    List<Hit<ExperienceDocument>> hits = runVectorSearch(
                            vector,
                            Math.max(effectiveLimit * VECTOR_FETCH_MULTIPLIER, DEFAULT_LIMIT * VECTOR_FETCH_MULTIPLIER),
                            excludedExperienceIds
                    );
                    mergeVectorCandidates(ranked, hits, experienceMap, weightedQuery);
                } catch (Exception e) {
                    log.warn("Vector search failed for retrieval query variant. Falling back to hybrid keyword scoring.", e);
                }
            }
        }

        // 카테고리 부스팅이 적용된 키워드 검색
        mergeKeywordCandidates(ranked, experienceMap.values(), weightedQueries, boost);

        return ranked.values().stream()
                .filter(RankedCandidate::hasSignal)
                .sorted(Comparator
                        .comparingDouble(RankedCandidate::finalScore)
                        .reversed()
                        .thenComparing(candidate -> safeText(candidate.experience().getTitle())))
                .limit(effectiveLimit)
                .map(this::toContextItem)
                .collect(Collectors.toList());
    }

    private Map<Long, Experience> loadAvailableExperiences(Set<Long> excludedExperienceIds) {
        return experienceRepository.findAll().stream()
                .filter(Objects::nonNull)
                .filter(experience -> !isExcluded(experience.getId(), excludedExperienceIds))
                .collect(Collectors.toMap(
                        Experience::getId,
                        experience -> experience,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private void mergeVectorCandidates(
            Map<Long, RankedCandidate> ranked,
            List<Hit<ExperienceDocument>> hits,
            Map<Long, Experience> experienceMap,
            WeightedQuery weightedQuery
    ) {
        for (Hit<ExperienceDocument> hit : hits) {
            if (hit == null || hit.source() == null) {
                continue;
            }

            ExperienceDocument document = hit.source();
            Long experienceId = document.getExperienceId();
            Experience experience = experienceMap.get(experienceId);
            if (experience == null) {
                continue;
            }

            RankedCandidate candidate = ranked.computeIfAbsent(
                    experienceId,
                    ignored -> new RankedCandidate(experienceId, experience)
            );
            candidate.mergeVectorScore(normalizeVectorScore(hit.score()) * weightedQuery.weight(), document.getChunkText());
        }
    }

    private void mergeKeywordCandidates(
            Map<Long, RankedCandidate> ranked,
            Collection<Experience> experiences,
            List<WeightedQuery> weightedQueries
    ) {
        mergeKeywordCandidates(ranked, experiences, weightedQueries, CategoryFieldBoost.none());
    }

    /**
     * 카테고리 부스팅이 적용된 키워드 후보 병합.
     *
     * @param boost 카테고리별 필드 추가 가중치 — 기본 가중치에 더해집니다.
     */
    private void mergeKeywordCandidates(
            Map<Long, RankedCandidate> ranked,
            Collection<Experience> experiences,
            List<WeightedQuery> weightedQueries,
            CategoryFieldBoost boost
    ) {
        for (Experience experience : experiences) {
            if (experience == null) {
                continue;
            }

            double keywordScore = calculateKeywordScore(experience, weightedQueries, boost);
            if (keywordScore <= 0) {
                continue;
            }

            RankedCandidate candidate = ranked.computeIfAbsent(
                    experience.getId(),
                    ignored -> new RankedCandidate(experience.getId(), experience)
            );
            candidate.mergeKeywordScore(keywordScore);
        }
    }

    private List<WeightedQuery> buildWeightedQueries(String primaryQuery, List<String> supportingQueries) {
        LinkedHashMap<String, Double> deduplicated = new LinkedHashMap<>();
        addWeightedQuery(deduplicated, primaryQuery, 1.0);

        if (supportingQueries != null) {
            for (String supportingQuery : supportingQueries) {
                addWeightedQuery(deduplicated, supportingQuery, 0.78);
            }
        }

        return deduplicated.entrySet().stream()
                .limit(MAX_QUERY_VARIANTS)
                .map(entry -> new WeightedQuery(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    private void addWeightedQuery(Map<String, Double> target, String rawQuery, double weight) {
        String normalized = safeText(rawQuery).replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return;
        }

        String comparable = normalized.toLowerCase(Locale.ROOT);
        target.merge(comparable, weight, Math::max);
    }

    private boolean hasIndexedDocuments() {
        try {
            return documentRepository.count() > 0;
        } catch (Exception e) {
            log.warn("Unable to inspect experience documents for vector search", e);
            return false;
        }
    }

    private float[] computeEmbedding(String text) {
        if (openAiApiKey == null || openAiApiKey.isBlank() || "demo".equalsIgnoreCase(openAiApiKey)) {
            log.debug("OpenAI key missing or demo, skipping embedding for {}", text);
            return null;
        }

        try {
            Embedding embedding = getEmbeddingModel().embed(text).content();
            return embedding.vector();
        } catch (Exception e) {
            log.warn("Failed to compute embedding for query, falling back to keyword scoring", e);
            return null;
        }
    }

    private EmbeddingModel getEmbeddingModel() {
        EmbeddingModel current = embeddingModel;
        if (current != null) {
            return current;
        }

        synchronized (this) {
            if (embeddingModel == null) {
                embeddingModel = OpenAiEmbeddingModel.builder()
                        .apiKey(openAiApiKey)
                        .modelName("text-embedding-3-small")
                        .build();
            }
            return embeddingModel;
        }
    }

    private List<Hit<ExperienceDocument>> runVectorSearch(
            float[] vector,
            int fetchLimit,
            Set<Long> excludedExperienceIds
    ) {
        List<Float> vectorValues = toVectorList(vector);

        Script script = Script.of(s -> s
                .source("cosineSimilarity(params.query_vector, 'embedding') + 1.0")
                .params(Map.of("query_vector", JsonData.of(vectorValues))));

        int requestedSize = fetchLimit + Math.max(0, excludedExperienceIds == null ? 0 : excludedExperienceIds.size());

        SearchRequest request = SearchRequest.of(builder -> builder
                .index(INDEX_NAME)
                .size(Math.max(fetchLimit, requestedSize))
                .query(query -> query
                        .scriptScore(score -> score
                                .query(inner -> inner.matchAll(match -> match))
                                .script(script)
                        )
                ));

        SearchResponse<ExperienceDocument> response;
        try {
            response = elasticsearchClient.search(request, ExperienceDocument.class);
        } catch (IOException e) {
            throw new IllegalStateException("Vector search request failed", e);
        }

        return response.hits().hits().stream()
                .filter(hit -> !isExcluded(hit, excludedExperienceIds))
                .collect(Collectors.toList());
    }

    private boolean isExcluded(Hit<ExperienceDocument> hit, Set<Long> excludedExperienceIds) {
        if (excludedExperienceIds == null || excludedExperienceIds.isEmpty() || hit == null) {
            return false;
        }
        return Optional.ofNullable(hit.source())
                .map(ExperienceDocument::getExperienceId)
                .map(experienceId -> isExcluded(experienceId, excludedExperienceIds))
                .orElse(false);
    }

    private boolean isExcluded(Long experienceId, Set<Long> excludedExperienceIds) {
        return experienceId != null
                && excludedExperienceIds != null
                && !excludedExperienceIds.isEmpty()
                && excludedExperienceIds.contains(experienceId);
    }

    private double calculateKeywordScore(Experience experience, List<WeightedQuery> weightedQueries) {
        return calculateKeywordScore(experience, weightedQueries, CategoryFieldBoost.none());
    }

    /**
     * 카테고리 부스팅이 적용된 키워드 점수 계산.
     *
     * <p>기본 필드 가중치에 {@link CategoryFieldBoost}의 추가 가중치를 더합니다.
     *
     * <pre>
     * 기본 가중치 예시 (EXPERIENCE 카테고리):
     *   title:       36 + 0  = 36
     *   techStack:   22 + 14 = 36  ← 기술 스택 강화
     *   metrics:     16 + 10 = 26  ← 성과 지표 강화
     *   role:        18 + 8  = 26  ← 역할 강화
     *   description: 24 + 0  = 24
     * </pre>
     */
    private double calculateKeywordScore(Experience experience, List<WeightedQuery> weightedQueries,
            CategoryFieldBoost boost) {
        if (weightedQueries == null || weightedQueries.isEmpty() || experience == null) {
            return 0;
        }

        double bestScore = 0;
        Set<String> titleTokens       = tokenize(experience.getTitle());
        Set<String> categoryTokens    = tokenize(experience.getCategory());
        Set<String> descriptionTokens = tokenize(experience.getDescription());
        Set<String> roleTokens        = tokenize(experience.getRole());
        Set<String> rawTokens         = tokenize(experience.getRawContent());
        Set<String> techStackTokens   = tokenize(compactStructuredText(experience.getTechStack(), 240));
        Set<String> metricsTokens     = tokenize(compactStructuredText(experience.getMetrics(), 240));
        Set<String> fileNameTokens    = tokenize(experience.getOriginalFileName());
        String normalizedCorpus = normalizeComparablePhrase(String.join(" ",
                safeText(experience.getTitle()),
                safeText(experience.getCategory()),
                safeText(experience.getDescription()),
                safeText(experience.getRole()),
                safeText(experience.getRawContent()),
                safeText(experience.getTechStack()),
                safeText(experience.getMetrics()),
                safeText(experience.getOriginalFileName())));

        for (WeightedQuery weightedQuery : weightedQueries) {
            Set<String> queryTokens = tokenize(weightedQuery.text());
            if (queryTokens.isEmpty()) {
                continue;
            }

            double queryScore = 0;
            // 기본 가중치 + 카테고리 부스팅 추가분
            queryScore += overlapScore(queryTokens, titleTokens,       36 + boost.titleBoost());
            queryScore += overlapScore(queryTokens, roleTokens,        18 + boost.roleBoost());
            queryScore += overlapScore(queryTokens, techStackTokens,   22 + boost.techStackBoost());
            queryScore += overlapScore(queryTokens, metricsTokens,     16 + boost.metricsBoost());
            queryScore += overlapScore(queryTokens, descriptionTokens, 24 + boost.descriptionBoost());
            queryScore += overlapScore(queryTokens, rawTokens,         16 + boost.rawContentBoost());
            queryScore += overlapScore(queryTokens, categoryTokens,    10 + boost.categoryBoost());
            queryScore += overlapScore(queryTokens, fileNameTokens,    8);

            String normalizedQuery = normalizeComparablePhrase(weightedQuery.text());
            if (!normalizedQuery.isBlank() && normalizedCorpus.contains(normalizedQuery)) {
                queryScore += 12;
            }

            bestScore = Math.max(bestScore, queryScore * weightedQuery.weight());
        }

        return Math.min(99, bestScore);
    }

    private double overlapScore(Set<String> queryTokens, Set<String> fieldTokens, double weight) {
        if (queryTokens == null || queryTokens.isEmpty() || fieldTokens == null || fieldTokens.isEmpty()) {
            return 0;
        }

        long matches = queryTokens.stream()
                .filter(fieldTokens::contains)
                .count();
        if (matches == 0) {
            return 0;
        }

        double coverage = (double) matches / queryTokens.size();
        double density = (double) matches / fieldTokens.size();
        return weight * Math.min(1.0, coverage + (density * 0.35));
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptySet();
        }

        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        Matcher matcher = TOKEN_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() < 2 || STOPWORDS.contains(token)) {
                continue;
            }
            tokens.add(token);
        }
        return tokens;
    }

    private ExperienceContextResponse.ContextItem toContextItem(RankedCandidate candidate) {
        Experience experience = candidate.experience();
        String title = safeText(experience.getTitle());
        if (title.isBlank()) {
            title = "Untitled Experience";
        }

        return ExperienceContextResponse.ContextItem.builder()
                .id("exp-" + candidate.experienceId())
                .experienceTitle(title)
                .relevantPart(buildRelevantPart(candidate))
                .relevanceScore((int) Math.round(candidate.finalScore()))
                .build();
    }

    private String buildRelevantPart(RankedCandidate candidate) {
        Experience experience = candidate.experience();
        List<String> segments = new ArrayList<>();

        String role = safeText(experience.getRole());
        String period = safeText(experience.getPeriod());
        String rolePeriod = joinNonBlank(" | ",
                role.isBlank() ? "" : "Role: " + role,
                period.isBlank() ? "" : "Period: " + period);
        if (!rolePeriod.isBlank()) {
            segments.add(rolePeriod);
        }

        String techStack = compactStructuredText(experience.getTechStack(), 90);
        if (!techStack.isBlank()) {
            segments.add("Stack: " + techStack);
        }

        String metrics = compactStructuredText(experience.getMetrics(), 90);
        if (!metrics.isBlank()) {
            segments.add("Outcome: " + metrics);
        }

        String snippet = firstNonBlank(
                candidate.bestChunk(),
                experience.getDescription(),
                experience.getRawContent(),
                experience.getRole());
        if (!snippet.isBlank()) {
            segments.add("Relevant detail: " + truncate(cleanSnippet(snippet), 180));
        }

        return truncate(String.join(" | ", segments), MAX_SNIPPET_LENGTH);
    }

    private String compactStructuredText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = value
                .replaceAll("[\\[\\]{}\"]", "")
                .replaceAll("\\s*:\\s*", ": ")
                .replaceAll("\\s*,\\s*", ", ")
                .replaceAll("\\s+", " ")
                .trim();

        return truncate(normalized, maxLength);
    }

    private String cleanSnippet(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        return value.replaceAll("\\s+", " ").trim();
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

    private String joinNonBlank(String delimiter, String... values) {
        return Arrays.stream(values)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(delimiter));
    }

    private double normalizeVectorScore(Double rawScore) {
        if (rawScore == null) {
            return 0;
        }
        return Math.max(0, Math.min(99, (rawScore - 1.0d) * 100.0d));
    }

    private String normalizeComparablePhrase(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\u3131-\\u318E\\uAC00-\\uD7A3]+", "");
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }

        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private List<Float> toVectorList(float[] vector) {
        List<Float> result = new ArrayList<>(vector.length);
        for (float value : vector) {
            result.add(value);
        }
        return result;
    }

    private record WeightedQuery(String text, double weight) {
    }

    private static final class RankedCandidate {
        private static final double HYBRID_VECTOR_WEIGHT = 0.62;
        private static final double HYBRID_KEYWORD_WEIGHT = 0.38;

        private final Long experienceId;
        private final Experience experience;
        private double vectorScore = 0;
        private double keywordScore = 0;
        private String bestChunk = "";

        private RankedCandidate(Long experienceId, Experience experience) {
            this.experienceId = experienceId;
            this.experience = experience;
        }

        private Long experienceId() {
            return experienceId;
        }

        private Experience experience() {
            return experience;
        }

        private String bestChunk() {
            return bestChunk;
        }

        private void mergeVectorScore(double score, String chunkText) {
            if (score > vectorScore) {
                vectorScore = score;
                bestChunk = chunkText == null ? "" : chunkText.trim();
            }
        }

        private void mergeKeywordScore(double score) {
            keywordScore = Math.max(keywordScore, score);
        }

        private boolean hasSignal() {
            return vectorScore > 0 || keywordScore > 0;
        }

        private double finalScore() {
            if (vectorScore > 0 && keywordScore > 0) {
                return Math.min(99, (vectorScore * HYBRID_VECTOR_WEIGHT) + (keywordScore * HYBRID_KEYWORD_WEIGHT) + 6);
            }
            return Math.min(99, Math.max(vectorScore, keywordScore));
        }
    }
}
