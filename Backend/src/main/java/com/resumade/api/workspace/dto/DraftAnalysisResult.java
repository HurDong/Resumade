package com.resumade.api.workspace.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
@Builder
public class DraftAnalysisResult {
    private List<Mistranslation> mistranslations;
    private AiReviewReport aiReviewReport;
    private String humanPatchedText;

    @Getter
    @Setter
    @Builder
    public static class Mistranslation {
        private String id;
        private String issueType;
        private String original;
        private String originalSentence;
        private String translated;
        private String suggestion;
        private String severity;
        private String translatedSentence;
        private String suggestedSentence;
        private String reason;
    }

    @Getter
    @Setter
    @Builder
    public static class AiReviewReport {
        private String summary;
        private String taggedOriginalText;
        private String taggedWashedText;
    }
}
