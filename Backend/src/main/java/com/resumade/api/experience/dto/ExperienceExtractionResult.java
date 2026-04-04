package com.resumade.api.experience.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExperienceExtractionResult {
    private String title;
    private String category;
    private String description;
    private String origin;
    private List<String> techStack;
    private List<String> metrics;
    private List<String> overallTechStack;
    private List<String> jobKeywords;
    private List<String> questionTypes;
    private String period;
    private String role;
    private String organization;
    private List<Facet> facets;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Facet {
        private String title;
        private List<String> situation;
        private List<String> role;
        private List<String> judgment;
        private List<String> actions;
        private List<String> results;
        private List<String> techStack;
        private List<String> jobKeywords;
        private List<String> questionTypes;
    }
}
