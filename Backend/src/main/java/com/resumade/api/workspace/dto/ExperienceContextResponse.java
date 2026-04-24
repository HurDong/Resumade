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
        private Long experienceId;
        private Long facetId;
        private Long unitId;
        private String unitType;
        private String experienceTitle;
        private String facetTitle;
        private List<String> intentTags;
        private String relevantPart;
        private int relevanceScore;
    }
}
