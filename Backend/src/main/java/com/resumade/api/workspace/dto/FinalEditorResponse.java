package com.resumade.api.workspace.dto;

import java.time.LocalDateTime;
import java.util.List;

public record FinalEditorResponse(
        Long questionId,
        String questionText,
        Integer maxLength,
        String originalDraft,
        String washedDraft,
        String finalText,
        String selectedTitle,
        String companyName,
        String position,
        Long applicationId,
        LocalDateTime updatedAt,
        List<TitleSuggestionResponse.TitleCandidate> titleCandidates,
        String savedFinalText
) {}
