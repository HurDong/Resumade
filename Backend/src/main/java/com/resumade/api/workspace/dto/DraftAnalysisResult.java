package com.resumade.api.workspace.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

@Getter
@Builder
public class DraftAnalysisResult {
    private List<Mistranslation> mistranslations;
    private AiReviewReport aiReviewReport;

    @Getter
    @Builder
    public static class Mistranslation {
        private String original;
        private String translated;
        private String suggestion;
        private String severity;
    }

    @Getter
    @Builder
    public static class AiReviewReport {
        private String summary;
        private int overallScore;
        private int technicalAccuracy;
        private int readability;
    }
}
