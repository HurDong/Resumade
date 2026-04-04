package com.resumade.api.technote.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TechNoteRequest(
        String title,
        String category,
        String content
) {}
