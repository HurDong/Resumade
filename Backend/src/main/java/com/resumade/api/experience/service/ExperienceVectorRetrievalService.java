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
import com.resumade.api.experience.domain.ExperienceFacet;
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
    private static final int MAX_SNIPPET_LENGTH = 420;
    private static final int MAX_QUERY_VARIANTS = 4;
    private static final int VECTOR_FETCH_MULTIPLIER = 4;

    private static final Map<QuestionCategory, CategoryFieldBoost> CATEGORY_BOOSTS = Map.of(
            QuestionCategory.MOTIVATION, new CategoryFieldBoost(0, 10, 4, 6, 8, 8, 0),
            QuestionCategory.EXPERIENCE, new CategoryFieldBoost(0, 4, 14, 10, 10, 4, 0),
            QuestionCategory.PROBLEM_SOLVING, new CategoryFieldBoost(0, 14, 0, 6, 4, 10, 0),
            QuestionCategory.COLLABORATION, new CategoryFieldBoost(0, 10, 0, 6, 14, 8, 0),
            QuestionCategory.PERSONAL_GROWTH, CategoryFieldBoost.none(), // RAG 우회 대상 — Personal Story Vault로 처리
            QuestionCategory.CULTURE_FIT, new CategoryFieldBoost(0, 10, 2, 6, 10, 12, 0),
            QuestionCategory.TREND_INSIGHT, new CategoryFieldBoost(6, 8, 6, 8, 6, 8, 0),
            QuestionCategory.DEFAULT, CategoryFieldBoost.none()
    );

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{IsAlphabetic}\\p{IsDigit}\\u3131-\\u318E\\uAC00-\\uD7A3+#._-]+");
    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "for", "with", "from", "that", "this", "your", "into", "self", "intro", "introduction",
            "resume", "question", "company", "position", "role", "experience", "project", "draft", "paragraph",
            "focus", "tone", "keep", "avoid", "more", "detail", "details", "specific", "specificity", "structure",
            "문항", "질문", "답변", "자소서", "자기소개서", "회사", "기업", "직무", "경험", "프로젝트", "역량",
            "성과", "작업", "내용", "문단", "구조", "수정", "보완", "추가", "요청", "구체", "강조", "중심",
            "기반", "통해", "위해", "대해", "관련", "있는", "입니다", "입니다.", "있다", "있습니다", "과정"
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

    @Transactional(readOnly = true)
    public List<ExperienceContextResponse.ContextItem> search(
            String query,
            int limit,
            Set<Long> excludedExperienceIds,
            List<String> supportingQueries,
            QuestionCategory category
    ) {
        QuestionCategory effectiveCategory = category != null ? category : QuestionCategory.DEFAULT;
        CategoryFieldBoost boost = CATEGORY_BOOSTS.getOrDefault(effectiveCategory, CategoryFieldBoost.none());

        List<WeightedQuery> weightedQueries = buildWeightedQueries(query, supportingQueries);
        if (weightedQueries.isEmpty()) {
            return Collections.emptyList();
        }

        int effectiveLimit = Math.max(limit, DEFAULT_LIMIT);
        Map<String, SearchUnit> units = loadAvailableUnits(excludedExperienceIds);
        if (units.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, RankedCandidate> ranked = new LinkedHashMap<>();

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
                    mergeVectorCandidates(ranked, hits, units, weightedQuery);
                } catch (Exception e) {
                    log.warn("Vector search failed for retrieval query variant. Falling back to keyword scoring.", e);
                }
            }
        }

        mergeKeywordCandidates(ranked, units.values(), weightedQueries, boost);

        return ranked.values().stream()
                .filter(RankedCandidate::hasSignal)
                .sorted(Comparator
                        .comparingDouble(RankedCandidate::finalScore)
                        .reversed()
                        .thenComparing(candidate -> safeText(candidate.unit().experience().getTitle())))
                .limit(effectiveLimit)
                .map(this::toContextItem)
                .collect(Collectors.toList());
    }

    private Map<String, SearchUnit> loadAvailableUnits(Set<Long> excludedExperienceIds) {
        Map<String, SearchUnit> units = new LinkedHashMap<>();
        for (Experience experience : experienceRepository.findAllWithFacets()) {
            if (experience == null || isExcluded(experience.getId(), excludedExperienceIds)) {
                continue;
            }

            if (experience.getFacets() == null || experience.getFacets().isEmpty()) {
                SearchUnit unit = SearchUnit.forProject(experience);
                units.put(unit.unitKey(), unit);
                continue;
            }

            for (ExperienceFacet facet : experience.getFacets()) {
                if (facet == null) {
                    continue;
                }
                SearchUnit unit = SearchUnit.forFacet(experience, facet);
                units.put(unit.unitKey(), unit);
            }
        }
        return units;
    }

    private void mergeVectorCandidates(
            Map<String, RankedCandidate> ranked,
            List<Hit<ExperienceDocument>> hits,
            Map<String, SearchUnit> units,
            WeightedQuery weightedQuery
    ) {
        for (Hit<ExperienceDocument> hit : hits) {
            if (hit == null || hit.source() == null) {
                continue;
            }

            ExperienceDocument document = hit.source();
            String unitKey = SearchUnit.unitKey(document.getExperienceId(), document.getFacetId());
            SearchUnit unit = units.get(unitKey);
            if (unit == null) {
                continue;
            }

            RankedCandidate candidate = ranked.computeIfAbsent(unitKey, ignored -> new RankedCandidate(unit));
            candidate.mergeVectorScore(normalizeVectorScore(hit.score()) * weightedQuery.weight(), document.getChunkText());
        }
    }

    private void mergeKeywordCandidates(
            Map<String, RankedCandidate> ranked,
            Collection<SearchUnit> units,
            List<WeightedQuery> weightedQueries,
            CategoryFieldBoost boost
    ) {
        for (SearchUnit unit : units) {
            if (unit == null) {
                continue;
            }

            double keywordScore = calculateKeywordScore(unit, weightedQueries, boost);
            if (keywordScore <= 0) {
                continue;
            }

            RankedCandidate candidate = ranked.computeIfAbsent(unit.unitKey(), ignored -> new RankedCandidate(unit));
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

    private double calculateKeywordScore(
            SearchUnit unit,
            List<WeightedQuery> weightedQueries,
            CategoryFieldBoost boost
    ) {
        if (weightedQueries == null || weightedQueries.isEmpty() || unit == null) {
            return 0;
        }

        double bestScore = 0;
        Set<String> titleTokens = tokenize(unit.titleCorpus());
        Set<String> categoryTokens = tokenize(unit.categoryCorpus());
        Set<String> descriptionTokens = tokenize(unit.descriptionCorpus());
        Set<String> roleTokens = tokenize(unit.roleCorpus());
        Set<String> rawTokens = tokenize(unit.rawCorpus());
        Set<String> techStackTokens = tokenize(unit.techStackCorpus());
        Set<String> metricsTokens = tokenize(unit.resultCorpus());
        Set<String> fileNameTokens = tokenize(unit.fileNameCorpus());
        String normalizedCorpus = normalizeComparablePhrase(String.join(" ",
                unit.titleCorpus(),
                unit.categoryCorpus(),
                unit.descriptionCorpus(),
                unit.roleCorpus(),
                unit.rawCorpus(),
                unit.techStackCorpus(),
                unit.resultCorpus(),
                unit.fileNameCorpus()));

        for (WeightedQuery weightedQuery : weightedQueries) {
            Set<String> queryTokens = tokenize(weightedQuery.text());
            if (queryTokens.isEmpty()) {
                continue;
            }

            double queryScore = 0;
            queryScore += overlapScore(queryTokens, titleTokens, 40 + boost.titleBoost());
            queryScore += overlapScore(queryTokens, roleTokens, 20 + boost.roleBoost());
            queryScore += overlapScore(queryTokens, techStackTokens, 22 + boost.techStackBoost());
            queryScore += overlapScore(queryTokens, metricsTokens, 18 + boost.metricsBoost());
            queryScore += overlapScore(queryTokens, descriptionTokens, 26 + boost.descriptionBoost());
            queryScore += overlapScore(queryTokens, rawTokens, 16 + boost.rawContentBoost());
            queryScore += overlapScore(queryTokens, categoryTokens, 8 + boost.categoryBoost());
            queryScore += overlapScore(queryTokens, fileNameTokens, 6);

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
        SearchUnit unit = candidate.unit();
        Experience experience = unit.experience();
        String title = safeText(experience.getTitle());
        if (title.isBlank()) {
            title = "Untitled Experience";
        }

        return ExperienceContextResponse.ContextItem.builder()
                .id(unit.facetId() != null ? "facet-" + unit.facetId() : "exp-" + unit.experienceId())
                .experienceId(unit.experienceId())
                .facetId(unit.facetId())
                .experienceTitle(title)
                .facetTitle(unit.facetTitle())
                .relevantPart(buildRelevantPart(candidate))
                .relevanceScore((int) Math.round(candidate.finalScore()))
                .build();
    }

    private String buildRelevantPart(RankedCandidate candidate) {
        SearchUnit unit = candidate.unit();
        Experience experience = unit.experience();
        List<String> segments = new ArrayList<>();

        if (!safeText(unit.facetTitle()).isBlank()) {
            segments.add("Facet: " + unit.facetTitle());
        }

        String role = safeText(experience.getRole());
        String period = safeText(experience.getPeriod());
        String rolePeriod = joinNonBlank(" | ",
                role.isBlank() ? "" : "Role: " + role,
                period.isBlank() ? "" : "Period: " + period);
        if (!rolePeriod.isBlank()) {
            segments.add(rolePeriod);
        }

        String techStack = unit.techStackPreview();
        if (!techStack.isBlank()) {
            segments.add("Stack: " + techStack);
        }

        String outcome = unit.resultPreview();
        if (!outcome.isBlank()) {
            segments.add("Outcome: " + outcome);
        }

        String snippet = firstNonBlank(
                candidate.bestChunk(),
                unit.detailPreview(),
                experience.getDescription(),
                experience.getRawContent());
        if (!snippet.isBlank()) {
            segments.add("Relevant detail: " + truncate(cleanSnippet(snippet), 220));
        }

        return truncate(String.join(" | ", segments), MAX_SNIPPET_LENGTH);
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

    private static String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private static final Pattern SECTION_LABEL_PATTERN = Pattern.compile(
            "(?m)^[가-힣A-Za-z\\s]{1,15}:\\s*",
            Pattern.UNICODE_CHARACTER_CLASS
    );

    private static String cleanSnippet(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return SECTION_LABEL_PATTERN.matcher(value).replaceAll("").replaceAll("\\s+", " ").trim();
    }

    private static String firstNonBlank(String... values) {
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

    private static String joinNonBlank(String delimiter, String... values) {
        return Arrays.stream(values)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(delimiter));
    }

    private static String truncate(String value, int maxLength) {
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

    private List<String> readJsonArray(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String cleaned = raw.replaceAll("[\\[\\]{}\"]", "")
                .replaceAll("\\s*:\\s*", ": ")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.isBlank()) {
            return List.of();
        }
        return Arrays.stream(cleaned.split("\\s*,\\s*"))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }

    private record WeightedQuery(String text, double weight) {
    }

    private record CategoryFieldBoost(
            double titleBoost,
            double descriptionBoost,
            double techStackBoost,
            double metricsBoost,
            double roleBoost,
            double rawContentBoost,
            double categoryBoost
    ) {
        private static CategoryFieldBoost none() {
            return new CategoryFieldBoost(0, 0, 0, 0, 0, 0, 0);
        }
    }

    private record SearchUnit(String unitKey, Long experienceId, Long facetId, Experience experience, ExperienceFacet facet) {
        private static SearchUnit forProject(Experience experience) {
            return new SearchUnit(unitKey(experience.getId(), null), experience.getId(), null, experience, null);
        }

        private static SearchUnit forFacet(Experience experience, ExperienceFacet facet) {
            return new SearchUnit(unitKey(experience.getId(), facet.getId()), experience.getId(), facet.getId(), experience, facet);
        }

        private static String unitKey(Long experienceId, Long facetId) {
            return facetId != null ? "facet:" + facetId : "exp:" + experienceId;
        }

        private String facetTitle() {
            return facet == null ? "" : safe(facet.getTitle());
        }

        private String titleCorpus() {
            return joinNonBlank(" ", safe(experience.getTitle()), facetTitle());
        }

        private String categoryCorpus() {
            return safe(experience.getCategory());
        }

        private String descriptionCorpus() {
            return joinNonBlank(" ",
                    safe(experience.getDescription()),
                    joinJson(facet == null ? null : facet.getSituation()),
                    joinJson(facet == null ? null : facet.getJudgment()),
                    joinJson(facet == null ? null : facet.getActions()));
        }

        private String roleCorpus() {
            return joinNonBlank(" ", safe(experience.getRole()), joinJson(facet == null ? null : facet.getRole()));
        }

        private String rawCorpus() {
            return joinNonBlank(" ",
                    safe(experience.getRawContent()),
                    joinJson(facet == null ? null : facet.getSituation()),
                    joinJson(facet == null ? null : facet.getRole()),
                    joinJson(facet == null ? null : facet.getJudgment()),
                    joinJson(facet == null ? null : facet.getActions()),
                    joinJson(facet == null ? null : facet.getResults()));
        }

        private String techStackCorpus() {
            return joinNonBlank(" ",
                    joinJson(experience.getTechStack()),
                    joinJson(experience.getOverallTechStack()),
                    joinJson(facet == null ? null : facet.getTechStack()),
                    joinJson(facet == null ? null : facet.getJobKeywords()),
                    joinJson(experience.getJobKeywords()));
        }

        private String resultCorpus() {
            return joinNonBlank(" ", joinJson(experience.getMetrics()), joinJson(facet == null ? null : facet.getResults()));
        }

        private String fileNameCorpus() {
            return safe(experience.getOriginalFileName());
        }

        private String detailPreview() {
            return joinNonBlank(" ",
                    joinJson(facet == null ? null : facet.getSituation()),
                    joinJson(facet == null ? null : facet.getJudgment()),
                    joinJson(facet == null ? null : facet.getActions()),
                    joinJson(facet == null ? null : facet.getResults()));
        }

        private String techStackPreview() {
            return truncate(joinNonBlank(", ", joinJson(facet == null ? null : facet.getTechStack()), joinJson(experience.getTechStack())), 100);
        }

        private String resultPreview() {
            return truncate(joinNonBlank(", ", joinJson(facet == null ? null : facet.getResults()), joinJson(experience.getMetrics())), 110);
        }

        private static String joinJson(String raw) {
            if (raw == null || raw.isBlank()) {
                return "";
            }
            return raw.replaceAll("[\\[\\]{}\"]", "")
                    .replaceAll("\\s*:\\s*", ": ")
                    .replaceAll("\\s+", " ")
                    .trim();
        }

        private static String safe(String value) {
            return value == null ? "" : value.trim();
        }
    }

    private static final class RankedCandidate {
        private static final double HYBRID_VECTOR_WEIGHT = 0.62;
        private static final double HYBRID_KEYWORD_WEIGHT = 0.38;

        private final SearchUnit unit;
        private double vectorScore = 0;
        private double keywordScore = 0;
        private String bestChunk = "";

        private RankedCandidate(SearchUnit unit) {
            this.unit = unit;
        }

        private SearchUnit unit() {
            return unit;
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
