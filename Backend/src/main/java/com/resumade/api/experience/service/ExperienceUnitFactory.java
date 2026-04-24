package com.resumade.api.experience.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.experience.domain.Experience;
import com.resumade.api.experience.domain.ExperienceFacet;
import com.resumade.api.experience.domain.ExperienceUnit;
import com.resumade.api.experience.domain.ExperienceUnitType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class ExperienceUnitFactory {

    private final ObjectMapper objectMapper;

    public List<ExperienceUnit> buildUnits(Experience experience) {
        if (experience == null || experience.getFacets() == null || experience.getFacets().isEmpty()) {
            return List.of();
        }

        List<ExperienceUnit> units = new ArrayList<>();
        int order = 0;
        for (ExperienceFacet facet : experience.getFacets()) {
            if (facet == null) {
                continue;
            }

            List<String> facetTechStack = readJsonArray(facet.getTechStack());
            List<String> facetJobKeywords = readJsonArray(facet.getJobKeywords());
            List<String> facetQuestionTypes = readJsonArray(facet.getQuestionTypes());
            String techStackJson = writeJsonArray(firstNonEmpty(facetTechStack, readJsonArray(experience.getOverallTechStack())));
            String jobKeywordsJson = writeJsonArray(firstNonEmpty(facetJobKeywords, readJsonArray(experience.getJobKeywords())));
            String questionTypesJson = writeJsonArray(firstNonEmpty(facetQuestionTypes, readJsonArray(experience.getQuestionTypes())));

            order = appendUnits(units, facet, ExperienceUnitType.SITUATION, readJsonArray(facet.getSituation()),
                    techStackJson, jobKeywordsJson, questionTypesJson, order);
            order = appendUnits(units, facet, ExperienceUnitType.ROLE, readJsonArray(facet.getRole()),
                    techStackJson, jobKeywordsJson, questionTypesJson, order);
            order = appendUnits(units, facet, ExperienceUnitType.JUDGMENT, readJsonArray(facet.getJudgment()),
                    techStackJson, jobKeywordsJson, questionTypesJson, order);
            order = appendUnits(units, facet, ExperienceUnitType.ACTION, readJsonArray(facet.getActions()),
                    techStackJson, jobKeywordsJson, questionTypesJson, order);
            order = appendUnits(units, facet, ExperienceUnitType.RESULT, readJsonArray(facet.getResults()),
                    techStackJson, jobKeywordsJson, questionTypesJson, order);
            order = appendUnits(units, facet, ExperienceUnitType.TECH_STACK, facetTechStack,
                    techStackJson, jobKeywordsJson, questionTypesJson, order);
            order = appendUnits(units, facet, ExperienceUnitType.QUESTION_TYPE, facetQuestionTypes,
                    techStackJson, jobKeywordsJson, questionTypesJson, order);
        }
        return units;
    }

    private int appendUnits(
            List<ExperienceUnit> units,
            ExperienceFacet facet,
            ExperienceUnitType unitType,
            List<String> values,
            String techStackJson,
            String jobKeywordsJson,
            String questionTypesJson,
            int order
    ) {
        for (String value : values) {
            String text = sanitizeText(value);
            if (text.isBlank()) {
                continue;
            }
            units.add(ExperienceUnit.builder()
                    .facet(facet)
                    .unitType(unitType)
                    .text(text)
                    .intentTags(writeJsonArray(buildIntentTags(unitType, jobKeywordsJson, questionTypesJson)))
                    .techStack(techStackJson)
                    .jobKeywords(jobKeywordsJson)
                    .questionTypes(questionTypesJson)
                    .displayOrder(order++)
                    .build());
        }
        return order;
    }

    private List<String> buildIntentTags(ExperienceUnitType unitType, String jobKeywordsJson, String questionTypesJson) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        tags.add(unitType.name().toLowerCase(Locale.ROOT));
        tags.addAll(readJsonArray(jobKeywordsJson));
        tags.addAll(readJsonArray(questionTypesJson));
        return tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .limit(12)
                .toList();
    }

    private List<String> firstNonEmpty(List<String> primary, List<String> fallback) {
        return primary == null || primary.isEmpty() ? fallback : primary;
    }

    List<String> readJsonArray(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(raw);
            if (root.isArray()) {
                List<String> values = new ArrayList<>();
                for (JsonNode node : root) {
                    String value = sanitizeText(node.asText(""));
                    if (!value.isBlank() && !values.contains(value)) {
                        values.add(value);
                    }
                }
                return values;
            }
        } catch (Exception ignored) {
            // Fall through to legacy comma-separated parsing.
        }

        String cleaned = raw.replaceAll("[\\[\\]{}\"]", "")
                .replaceAll("\\s*:\\s*", ": ")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.isBlank()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (String token : cleaned.split("\\s*,\\s*")) {
            String value = sanitizeText(token);
            if (!value.isBlank() && !values.contains(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private String writeJsonArray(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String sanitizeText(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }
}
