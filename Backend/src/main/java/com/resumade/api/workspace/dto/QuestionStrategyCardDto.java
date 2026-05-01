package com.resumade.api.workspace.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class QuestionStrategyCardDto {

    private Long questionId;
    private String questionTitle;
    private String category;
    private String summary;
    private StrategyText intent;
    private String primaryClaim;
    private ExperiencePlan experiencePlan;
    private FitConnection fitConnection;
    private List<ParagraphPlan> paragraphPlan;
    private List<String> warnings;
    private String draftDirective;
    private List<String> confidenceNotes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StrategyText {
        private String title;
        private String detail;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExperiencePlan {
        private List<String> primaryExperiences;
        private List<String> facets;
        private List<String> proofPoints;
        private List<String> learningPoints;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FitConnection {
        private String companyAnchor;
        private String domainBridge;
        private List<String> lexicon;
        private List<String> evidence;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ParagraphPlan {
        private int paragraph;
        private String role;
        private List<String> contents;
    }
}
