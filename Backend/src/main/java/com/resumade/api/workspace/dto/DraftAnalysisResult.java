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
        /** originalDraft 내 original 문구의 시작 위치 (LLM 반환, 0-based inclusive) */
        private Integer originalStartIndex;
        /** originalDraft 내 original 문구의 종료 위치 (LLM 반환, 0-based exclusive) */
        private Integer originalEndIndex;
        /** washedKr 내 translated 문구의 시작 위치 (LLM 반환, 0-based inclusive) */
        private Integer startIndex;
        /** washedKr 내 translated 문구의 종료 위치 (LLM 반환, 0-based exclusive) */
        private Integer endIndex;
        /** 1.0 = 문구 발견됨(인덱스 검증 통과), 0.0 = 발견 안됨 */
        private Double matchConfidence;
    }

    @Getter
    @Setter
    @Builder
    public static class AiReviewReport {
        private String summary;
    }
}
