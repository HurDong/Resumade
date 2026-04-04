package com.resumade.api.coding.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.coding.domain.CodingProblem;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Getter
@Builder
public class CodingProblemResponse {

    private Long id;
    private String company;
    private String date;
    private String title;
    private List<String> types;
    private String platform;
    private Integer level;
    private String myApproach;
    private String betterApproach;
    private String betterCode;
    private String note;
    private LocalDateTime createdAt;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static CodingProblemResponse from(CodingProblem problem) {
        List<String> types = parseJsonList(problem.getTypes());
        return CodingProblemResponse.builder()
                .id(problem.getId())
                .company(problem.getCompany())
                .date(problem.getDate())
                .title(problem.getTitle())
                .types(types)
                .platform(problem.getPlatform())
                .level(problem.getLevel())
                .myApproach(problem.getMyApproach())
                .betterApproach(problem.getBetterApproach())
                .betterCode(problem.getBetterCode())
                .note(problem.getNote())
                .createdAt(problem.getCreatedAt())
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
