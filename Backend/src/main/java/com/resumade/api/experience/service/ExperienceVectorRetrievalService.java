package com.resumade.api.experience.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.resumade.api.experience.domain.Experience;
import com.resumade.api.experience.domain.ExperienceRepository;
import com.resumade.api.experience.document.ExperienceDocument;
import com.resumade.api.experience.document.ExperienceDocumentRepository;
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
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExperienceVectorRetrievalService {

    private static final String INDEX_NAME = "experience-docs";
    private static final int DEFAULT_LIMIT = 3;
    private static final int MAX_SNIPPET_LENGTH = 400;

    private final ExperienceRepository experienceRepository;
    private final ExperienceDocumentRepository documentRepository;
    private final ElasticsearchClient elasticsearchClient;

    @Value("${openai.api.key:demo}")
    private String openAiApiKey;

    @Transactional(readOnly = true)
    public List<ExperienceContextResponse.ContextItem> search(String query, int limit) {
        return search(query, limit, Collections.emptySet());
    }

    @Transactional(readOnly = true)
    public List<ExperienceContextResponse.ContextItem> search(
            String query, int limit, Set<Long> excludedExperienceIds) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        int effectiveLimit = Math.max(limit, DEFAULT_LIMIT);
        float[] vector = computeEmbedding(query);

        if (vector != null && hasIndexedDocuments()) {
            try {
                List<ExperienceContextResponse.ContextItem> vectorResults =
                        runVectorSearch(vector, effectiveLimit, excludedExperienceIds);
                if (!vectorResults.isEmpty()) {
                    return vectorResults;
                }
            } catch (Exception e) {
                log.warn("Vector search failed, falling back to keyword scoring", e);
            }
        }

        return fallbackScore(query, effectiveLimit, excludedExperienceIds);
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
            EmbeddingModel model = OpenAiEmbeddingModel.builder()
                    .apiKey(openAiApiKey)
                    .modelName("text-embedding-3-small")
                    .build();
            Embedding embedding = model.embed(text).content();
            return embedding.vector();
        } catch (Exception e) {
            log.warn("Failed to compute embedding for query, falling back to keyword scoring", e);
            return null;
        }
    }

    private List<ExperienceContextResponse.ContextItem> runVectorSearch(
            float[] vector, int limit, Set<Long> excludedExperienceIds) {
        List<Float> vectorValues = toVectorList(vector);

        Script script = Script.of(s -> s
                .source("cosineSimilarity(params.query_vector, 'embedding') + 1.0")
                .params(Collections.singletonMap("query_vector", JsonData.of(vectorValues))));

        int fetchLimit = limit + Math.max(0, excludedExperienceIds == null ? 0 : excludedExperienceIds.size());

        SearchRequest request = SearchRequest.of(builder -> builder
                .index(INDEX_NAME)
                .size(Math.max(limit, fetchLimit))
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
        List<Hit<ExperienceDocument>> filteredHits = response.hits().hits().stream()
                .filter(hit -> !isExcluded(hit, excludedExperienceIds))
                .collect(Collectors.toList());
        return mapHitsToContextItems(filteredHits, limit);
    }

    private boolean isExcluded(Hit<ExperienceDocument> hit, Set<Long> excludedExperienceIds) {
        if (excludedExperienceIds == null || excludedExperienceIds.isEmpty() || hit == null) {
            return false;
        }
        return Optional.ofNullable(hit.source())
                .map(ExperienceDocument::getExperienceId)
                .map(excludedExperienceIds::contains)
                .orElse(false);
    }

    private List<ExperienceContextResponse.ContextItem> mapHitsToContextItems(
            List<Hit<ExperienceDocument>> hits, int limit) {
        if (hits == null || hits.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> experienceIds = hits.stream()
                .map(hit -> Optional.ofNullable(hit.source()).map(ExperienceDocument::getExperienceId).orElse(null))
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, Experience> experienceMap = experienceRepository.findAllById(experienceIds).stream()
                .collect(Collectors.toMap(Experience::getId, Function.identity()));

        return hits.stream()
                .map(hit -> toContextItem(hit, experienceMap))
                .filter(Objects::nonNull)
                .limit(limit)
                .collect(Collectors.toList());
    }

    private ExperienceContextResponse.ContextItem toContextItem(
            Hit<ExperienceDocument> hit, Map<Long, Experience> experienceMap) {
        if (hit == null || hit.source() == null) {
            return null;
        }

        ExperienceDocument document = hit.source();
        Experience experience = experienceMap.get(document.getExperienceId());

        String title = experience != null && experience.getTitle() != null
                ? experience.getTitle()
                : "Experience " + document.getExperienceId();
        String chunk = Optional.ofNullable(document.getChunkText()).orElse("").trim();
        int score = mapScore(hit.score());

        return ExperienceContextResponse.ContextItem.builder()
                .id("exp-" + document.getExperienceId() + "-chunk-" + document.getId())
                .experienceTitle(title)
                .relevantPart(truncate(chunk, MAX_SNIPPET_LENGTH))
                .relevanceScore(score)
                .build();
    }

    private List<ExperienceContextResponse.ContextItem> fallbackScore(
            String query, int limit, Set<Long> excludedExperienceIds) {
        String normalized = query.toLowerCase(Locale.ROOT);

        return experienceRepository.findAll().stream()
                .map(exp -> mapFallbackItem(exp, normalized))
                .filter(Objects::nonNull)
                .filter(item -> !isExcluded(item, excludedExperienceIds))
                .sorted(Comparator.comparingInt(ExperienceContextResponse.ContextItem::getRelevanceScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    private ExperienceContextResponse.ContextItem mapFallbackItem(Experience experience, String normalizedQuery) {
        if (experience == null) {
            return null;
        }

        int score = calculateKeywordScore(experience, normalizedQuery);
        if (score <= 0) {
            return null;
        }

        String snippet = Optional.ofNullable(experience.getDescription())
                .or(() -> Optional.ofNullable(experience.getRawContent()))
                .or(() -> Optional.ofNullable(experience.getRole()))
                .orElse("")
                .trim();

        return ExperienceContextResponse.ContextItem.builder()
                .id("exp-" + experience.getId())
                .experienceTitle(Optional.ofNullable(experience.getTitle()).orElse("Untitled Experience"))
                .relevantPart(truncate(snippet, MAX_SNIPPET_LENGTH))
                .relevanceScore(score)
                .build();
    }

    private boolean isExcluded(ExperienceContextResponse.ContextItem item, Set<Long> excludedExperienceIds) {
        if (excludedExperienceIds == null || excludedExperienceIds.isEmpty() || item == null) {
            return false;
        }

        String id = item.getId();
        if (id == null || !id.startsWith("exp-")) {
            return false;
        }

        String[] parts = id.split("-");
        if (parts.length < 2) {
            return false;
        }

        try {
            Long experienceId = Long.parseLong(parts[1]);
            return excludedExperienceIds.contains(experienceId);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private int calculateKeywordScore(Experience experience, String normalizedQuery) {
        if (normalizedQuery == null || normalizedQuery.isBlank()) {
            return 0;
        }

        int score = 0;
        String title = Optional.ofNullable(experience.getTitle()).orElse("").toLowerCase(Locale.ROOT);
        String description = Optional.ofNullable(experience.getDescription()).orElse("").toLowerCase(Locale.ROOT);
        String raw = Optional.ofNullable(experience.getRawContent()).orElse("").toLowerCase(Locale.ROOT);

        if (title.contains(normalizedQuery)) {
            score += 50;
        }
        if (description.contains(normalizedQuery)) {
            score += 30;
        }
        if (raw.contains(normalizedQuery)) {
            score += 20;
        }

        return Math.min(score, 99);
    }

    private int mapScore(Double rawScore) {
        if (rawScore == null) {
            return 0;
        }
        return Math.max(0, Math.min(99, Math.round(rawScore.floatValue() * 40)));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "";
        }

        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private List<Float> toVectorList(float[] vector) {
        List<Float> result = new ArrayList<>(vector.length);
        for (float value : vector) {
            result.add(value);
        }
        return result;
    }
}
