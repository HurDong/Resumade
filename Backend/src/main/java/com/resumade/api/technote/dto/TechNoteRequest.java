package com.resumade.api.technote.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TechNoteRequest(
        String title,
        String category,
        String summary,
        List<String> conditions,
        String template,
        List<String> tags
) {}
