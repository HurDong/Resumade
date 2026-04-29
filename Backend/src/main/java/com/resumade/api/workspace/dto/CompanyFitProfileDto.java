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
public class CompanyFitProfileDto {

    private Focus focus;
    private String summary;
    private List<ProfileItem> businessAgenda;
    private List<ProfileItem> roleMission;
    private List<ProfileItem> hiringSignals;
    private List<ProfileItem> workStyle;
    private List<LexiconItem> domainLexicon;
    private List<ProfileItem> strategyWarnings;
    private List<EvidenceItem> evidence;
    private List<String> confidenceNotes;
    private List<String> searchQueries;
    private List<SearchSource> searchSources;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Focus {
        private String company;
        private String position;
        private String inferredBusinessUnit;
        private String inferredProduct;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProfileItem {
        private String title;
        private String detail;
        private String confidence;
        private List<String> evidenceIds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LexiconItem {
        private String term;
        private String usage;
        private String confidence;
        private List<String> evidenceIds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EvidenceItem {
        private String id;
        private String type;
        private String title;
        private String uri;
        private String summary;
        private String publishedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SearchSource {
        private String title;
        private String uri;
    }
}
