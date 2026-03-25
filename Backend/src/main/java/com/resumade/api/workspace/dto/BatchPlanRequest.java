package com.resumade.api.workspace.dto;

import lombok.Data;

import java.util.List;

@Data
public class BatchPlanRequest {
    private Long applicationId;
    private List<QuestionSnapshot> questions;

    @Data
    public static class QuestionSnapshot {
        private Long questionId;
        private String title;
        private Integer maxLength;
        private String userDirective;
        private String batchStrategyDirective;
        private String content;
        private String washedKr;
    }
}
