package com.resumade.api.workspace.prompt;

import java.util.List;

public record ExperienceNeed(
        String unit,
        String query,
        List<String> preferredUnitTypes,
        List<String> intentTags
) {
    public ExperienceNeed {
        unit = unit == null ? "" : unit.trim();
        query = query == null ? "" : query.trim();
        preferredUnitTypes = preferredUnitTypes == null ? List.of() : List.copyOf(preferredUnitTypes);
        intentTags = intentTags == null ? List.of() : List.copyOf(intentTags);
    }
}
