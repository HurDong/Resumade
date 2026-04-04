package com.resumade.api.coding.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CodingProblemRequest(
        String company,
        String date,
        String title,
        List<String> types,
        String platform,
        Integer level,
        String myApproach,
        String betterApproach,
        String betterCode,
        String note
) {}
