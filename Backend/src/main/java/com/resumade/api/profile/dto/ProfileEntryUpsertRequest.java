package com.resumade.api.profile.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProfileEntryUpsertRequest(
        String id,
        String category,
        String title,
        String organization,
        String dateLabel,
        String summary,
        String referenceId,
        String highlight
) {
}
