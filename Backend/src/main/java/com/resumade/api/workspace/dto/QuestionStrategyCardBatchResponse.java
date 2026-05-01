package com.resumade.api.workspace.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record QuestionStrategyCardBatchResponse(
        Long applicationId,
        String sourceType,
        String modelName,
        List<QuestionStrategyCardResponse> cards
) {
}
