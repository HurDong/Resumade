package com.resumade.api.experience.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ExperienceFacetResponse {
    private Long id;
    private Integer displayOrder;
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
