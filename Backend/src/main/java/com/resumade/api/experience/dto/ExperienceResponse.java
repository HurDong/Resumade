package com.resumade.api.experience.dto;

import com.resumade.api.experience.domain.Experience;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ExperienceResponse {
    private Long id;
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
    private String rawContent;
    private List<ExperienceFacetResponse> facets;

    public static ExperienceResponse from(
            Experience experience,
            List<String> techStack,
            List<String> metrics,
            List<String> overallTechStack,
            List<String> jobKeywords,
            List<String> questionTypes,
            List<ExperienceFacetResponse> facets
    ) {
        return ExperienceResponse.builder()
                .id(experience.getId())
                .title(experience.getTitle())
                .category(experience.getCategory())
                .description(experience.getDescription())
                .origin(experience.getOrigin())
                .techStack(techStack)
                .metrics(metrics)
                .overallTechStack(overallTechStack)
                .jobKeywords(jobKeywords)
                .questionTypes(questionTypes)
                .period(experience.getPeriod())
                .role(experience.getRole())
                .organization(experience.getOrganization())
                .rawContent(experience.getRawContent())
                .facets(facets)
                .build();
    }
}
