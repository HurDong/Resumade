package com.resumade.api.workspace.dto;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record CompanyFitProfileResponse(
        Long id,
        Long applicationId,
        CompanyFitProfileDto profile,
        String reviewNote,
        String modelName,
        String groundingStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
