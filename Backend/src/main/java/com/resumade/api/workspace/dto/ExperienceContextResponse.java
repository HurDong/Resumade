package com.resumade.api.workspace.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExperienceContextResponse {
    private List<ContextItem> extractedContext;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContextItem {
        private String id;
        private String experienceTitle;
        private String relevantPart;
        private int relevanceScore;
    }
}
