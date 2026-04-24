package com.resumade.api.workspace.prompt;

import java.util.List;

public record QuestionDraftPlan(
        QuestionCategory primaryCategory,
        boolean compound,
        String primaryIntent,
        List<String> secondaryIntents,
        String lengthBand,
        int paragraphCount,
        AnswerContract answerContract,
        List<String> contentUnits,
        List<String> compressionPlan,
        List<ExperienceNeed> experienceNeeds,
        DraftBlueprint draftBlueprint,
        String framingNote,
        List<String> requiredElements,
        List<String> ragKeywords
) {
    public QuestionDraftPlan {
        primaryCategory = primaryCategory == null ? QuestionCategory.DEFAULT : primaryCategory;
        primaryIntent = primaryIntent == null ? "" : primaryIntent.trim();
        secondaryIntents = secondaryIntents == null ? List.of() : List.copyOf(secondaryIntents);
        lengthBand = lengthBand == null ? "medium" : lengthBand.trim();
        paragraphCount = Math.max(1, paragraphCount);
        answerContract = answerContract == null ? new AnswerContract(List.of(), List.of(), List.of()) : answerContract;
        contentUnits = contentUnits == null ? List.of() : List.copyOf(contentUnits);
        compressionPlan = compressionPlan == null ? List.of() : List.copyOf(compressionPlan);
        experienceNeeds = experienceNeeds == null ? List.of() : List.copyOf(experienceNeeds);
        framingNote = framingNote == null ? "" : framingNote.trim();
        requiredElements = requiredElements == null ? List.of() : List.copyOf(requiredElements);
        ragKeywords = ragKeywords == null ? List.of() : List.copyOf(ragKeywords);
    }

    public QuestionProfile toProfile() {
        return new QuestionProfile(primaryCategory, compound, framingNote.isBlank() ? null : framingNote, requiredElements, ragKeywords);
    }
}
