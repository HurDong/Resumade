package com.resumade.api.workspace.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class SentencePairAnalysisResult {

    private List<SentenceMistranslation> mistranslations;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class SentenceMistranslation {
        /** 원본 초안 문장에서 발견된 정확한 용어 (single word/term) */
        private String original;
        /** 세탁본 문장에서 오역된 정확한 용어 (single word/term) */
        private String translated;
        /** TERM_WEAKENED | PROPER_NOUN_CHANGED | METRIC_DROPPED */
        private String issueType;
        /** 오역 이유 (1줄) */
        private String reason;
        /** 권장 교정 용어 */
        private String suggestion;
    }
}
