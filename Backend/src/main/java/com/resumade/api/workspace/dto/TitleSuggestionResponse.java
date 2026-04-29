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
public class TitleSuggestionResponse {
    private String currentTitle;
    private List<TitleCandidate> candidates;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TitleCandidate {
        private String title;
        private int score;
        private String reason;
        private String pattern;
        private String risk;
        private boolean recommended;
    }
}
