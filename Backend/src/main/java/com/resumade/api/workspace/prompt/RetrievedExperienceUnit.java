package com.resumade.api.workspace.prompt;

import java.util.List;

public record RetrievedExperienceUnit(
        Long experienceId,
        Long facetId,
        Long unitId,
        String experienceTitle,
        String facetTitle,
        String unitType,
        List<String> intentTags,
        String relevantPart,
        int relevanceScore
) {
}
