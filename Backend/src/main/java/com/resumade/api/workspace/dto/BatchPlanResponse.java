package com.resumade.api.workspace.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BatchPlanResponse {
    private String coverageSummary;
    private List<String> globalGuardrails;
    private String model;
    private OverlapValidation overlapValidation;
    private List<Assignment> assignments;

    @Data
    @Builder
    public static class Assignment {
        private Long questionId;
        private String questionTitle;

        // Step 1: Intent classification
        private String questionIntentTag;
        private String intentRationale;

        // Step 2: Facet-level experience mapping
        private List<String> primaryExperiences;
        private List<String> experienceFacets;

        // Step 3: Domain bridge
        private String domainBridge;

        // Differentiation
        private String angle;
        private List<String> focusDetails;
        private List<String> learningPoints;
        private List<String> avoidDetails;
        private String reasoning;
        private String directivePrefix;
    }

    @Data
    @Builder
    public static class OverlapValidation {
        private boolean isClean;
        private List<String> conflictPairs;
        private String resolution;
    }
}
