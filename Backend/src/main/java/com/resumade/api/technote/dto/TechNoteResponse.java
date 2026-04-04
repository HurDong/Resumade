package com.resumade.api.technote.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.technote.domain.TechNote;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Getter
@Builder
public class TechNoteResponse {

    private Long id;
    private String title;
    private String category;
    private String summary;
    private List<String> conditions;
    private String template;
    private List<String> tags;
    private LocalDateTime createdAt;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static TechNoteResponse from(TechNote note) {
        return TechNoteResponse.builder()
                .id(note.getId())
                .title(note.getTitle())
                .category(note.getCategory())
                .summary(note.getSummary())
                .conditions(parseJsonList(note.getConditions()))
                .template(note.getTemplate())
                .tags(parseJsonList(note.getTags()))
                .createdAt(note.getCreatedAt())
                .build();
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
