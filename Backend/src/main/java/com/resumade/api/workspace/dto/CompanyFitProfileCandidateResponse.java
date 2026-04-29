package com.resumade.api.workspace.dto;

import lombok.Builder;

import java.time.Instant;

@Builder
public record CompanyFitProfileCandidateResponse(
        String uuid,
        Long applicationId,
        CompanyFitProfileDto profile,
        String modelName,
        String groundingStatus,
        Instant expiresAt
) {
}
