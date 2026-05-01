package com.resumade.api.workspace.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder
public record QuestionStrategyCardCandidateResponse(
        String uuid,
        Long applicationId,
        String sourceType,
        String modelName,
        Instant expiresAt,
        List<QuestionStrategyCardResponse> cards
) {
}
