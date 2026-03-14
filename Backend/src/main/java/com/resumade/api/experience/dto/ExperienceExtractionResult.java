package com.resumade.api.experience.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExperienceExtractionResult {
    private String title;
    private String category;
    private String description;
    private List<String> techStack;
    private List<String> metrics;
    private String period;
    private String role;
}
