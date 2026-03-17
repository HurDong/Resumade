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
public class CompanyResearchResponse {
    private Focus focus;
    private String executiveSummary;
    private List<String> businessContext;
    private List<String> serviceLandscape;
    private List<String> roleScope;
    private List<String> techSignals;
    private List<String> motivationHooks;
    private List<String> serviceHooks;
    private List<String> resumeAngles;
    private List<String> interviewSignals;
    private String recommendedNarrative;
    private List<String> followUpQuestions;
    private List<String> confidenceNotes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Focus {
        private String company;
        private String position;
        private String businessUnit;
        private String targetService;
        private String focusRole;
        private String techFocus;
        private String questionGoal;
    }
}
