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
    private List<Assignment> assignments;

    @Data
    @Builder
    public static class Assignment {
        private Long questionId;
        private String questionTitle;
        private List<String> primaryExperiences;
        private String angle;
        private List<String> focusDetails;
        private List<String> learningPoints;
        private List<String> avoidDetails;
        private String reasoning;
        private String directivePrefix;
    }
}
