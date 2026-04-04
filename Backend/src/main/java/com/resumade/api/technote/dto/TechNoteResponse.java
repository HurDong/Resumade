package com.resumade.api.technote.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.technote.domain.TechNote;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

public record TechNoteResponse(
        Long id,
        String title,
        String category,
        String content,
        Integer sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static TechNoteResponse from(TechNote note) {
        return new TechNoteResponse(
                note.getId(),
                note.getTitle(),
                normalizeCategory(note.getCategory()),
                resolveContent(note),
                note.getSortOrder(),
                note.getCreatedAt(),
                note.getUpdatedAt()
        );
    }

    private static String resolveContent(TechNote note) {
        if (note.getContent() != null && !note.getContent().isBlank()) {
            return note.getContent();
        }

        StringBuilder builder = new StringBuilder();
        builder.append("# ").append(safe(note.getTitle(), "Untitled")).append("\n\n");

        if (note.getSummary() != null && !note.getSummary().isBlank()) {
            builder.append("## 빠르게 기억할 포인트\n");
            builder.append(note.getSummary().trim()).append("\n\n");
        }

        List<String> conditions = parseJsonList(note.getConditions());
        if (!conditions.isEmpty()) {
            builder.append("## 체크 포인트\n");
            conditions.forEach(condition -> builder.append("- ").append(condition).append("\n"));
            builder.append("\n");
        }

        if (note.getTemplate() != null && !note.getTemplate().isBlank()) {
            builder.append("## Java 템플릿\n");
            builder.append(renderTemplateBlock(note.getTemplate().trim())).append("\n\n");
        }

        List<String> tags = parseJsonList(note.getTags());
        if (!tags.isEmpty()) {
            builder.append("## 키워드\n");
            tags.forEach(tag -> builder.append("- ").append(tag).append("\n"));
        }

        return builder.toString().trim();
    }

    private static String renderTemplateBlock(String template) {
        if (template.contains("```")) {
            return template;
        }

        return """
                ```java
                %s
                ```
                """.formatted(template);
    }

    private static String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "알고리즘";
        }

        String normalized = category.trim().toLowerCase();
        if (normalized.contains("문법") || normalized.contains("syntax")) {
            return "문법";
        }
        return "알고리즘";
    }

    private static String safe(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static List<String> parseJsonList(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
