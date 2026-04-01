package com.resumade.api.workspace.dto;

import com.resumade.api.workspace.prompt.QuestionCategory;
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
        /** 유저가 직접 지정한 카테고리. null이면 AI 자동 분류 사용 */
        private QuestionCategory category;
    }
}
