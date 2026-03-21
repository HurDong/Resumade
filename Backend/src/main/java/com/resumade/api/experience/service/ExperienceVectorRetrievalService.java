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
        return search(query, limit, Collections.emptySet(), Collections.emptyList());
    }

    @Transactional(readOnly = true)
    public List<ExperienceContextResponse.ContextItem> search(String query, int limit, Set<Long> excludedExperienceIds) {
        return search(query, limit, excludedExperienceIds, Collections.emptyList());
    }

    @Transactional(readOnly = true)
    public List<ExperienceContextResponse.ContextItem> search(
            String query,
            int limit,
            Set<Long> excludedExperienceIds,
            List<String> supportingQueries
    ) {
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

        mergeKeywordCandidates(ranked, experienceMap.values(), weightedQueries);

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
        for (Experience experience : experiences) {
            if (experience == null) {
                continue;
            }

            double keywordScore = calculateKeywordScore(experience, weightedQueries);
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
        if (weightedQueries == null || weightedQueries.isEmpty() || experience == null) {
            return 0;
        }

        double bestScore = 0;
        Set<String> titleTokens = tokenize(experience.getTitle());
        Set<String> categoryTokens = tokenize(experience.getCategory());
        Set<String> descriptionTokens = tokenize(experience.getDescription());
        Set<String> roleTokens = tokenize(experience.getRole());
        Set<String> rawTokens = tokenize(experience.getRawContent());
        Set<String> techStackTokens = tokenize(compactStructuredText(experience.getTechStack(), 240));
        Set<String> metricsTokens = tokenize(compactStructuredText(experience.getMetrics(), 240));
        Set<String> fileNameTokens = tokenize(experience.getOriginalFileName());
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
            queryScore += overlapScore(queryTokens, titleTokens, 36);
            queryScore += overlapScore(queryTokens, roleTokens, 18);
            queryScore += overlapScore(queryTokens, techStackTokens, 22);
            queryScore += overlapScore(queryTokens, metricsTokens, 16);
            queryScore += overlapScore(queryTokens, descriptionTokens, 24);
            queryScore += overlapScore(queryTokens, rawTokens, 16);
            queryScore += overlapScore(queryTokens, categoryTokens, 10);
            queryScore += overlapScore(queryTokens, fileNameTokens, 8);

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
