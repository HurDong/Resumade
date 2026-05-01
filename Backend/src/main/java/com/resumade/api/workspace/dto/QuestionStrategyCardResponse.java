package com.resumade.api.workspace.dto;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record QuestionStrategyCardResponse(
        Long id,
        Long applicationId,
        Long questionId,
        QuestionStrategyCardDto card,
        String directivePrefix,
        String reviewNote,
        String sourceType,
        String modelName,
        Long fitProfileId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
