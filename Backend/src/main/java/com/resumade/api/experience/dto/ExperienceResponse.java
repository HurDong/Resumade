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
    private List<String> techStack;
    private List<String> metrics;
    private String period;
    private String role;
    
    public static ExperienceResponse from(Experience experience, List<String> techStack, List<String> metrics) {
        return ExperienceResponse.builder()
                .id(experience.getId())
                .title(experience.getTitle())
                .category(experience.getCategory())
                .description(experience.getDescription())
                .techStack(techStack)
                .metrics(metrics)
                .period(experience.getPeriod())
                .role(experience.getRole())
                .build();
    }
}
