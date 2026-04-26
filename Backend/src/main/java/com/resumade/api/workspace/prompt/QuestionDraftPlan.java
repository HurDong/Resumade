package com.resumade.api.workspace.prompt;

import java.util.List;

public record QuestionDraftPlan(
        QuestionCategory primaryCategory,
        String questionIntent,
        String answerPosture,
        String evidencePolicy,
        String companyConnectionPolicy,
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
        questionIntent = questionIntent == null || questionIntent.isBlank() ? primaryCategory.name() : questionIntent.trim();
        answerPosture = answerPosture == null || answerPosture.isBlank() ? "COMPETENCY_PROOF" : answerPosture.trim();
        evidencePolicy = evidencePolicy == null || evidencePolicy.isBlank() ? "USE_RELEVANT_VERIFIED_FACTS" : evidencePolicy.trim();
        companyConnectionPolicy = companyConnectionPolicy == null || companyConnectionPolicy.isBlank()
                ? "ROLE_RELEVANT"
                : companyConnectionPolicy.trim();
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
