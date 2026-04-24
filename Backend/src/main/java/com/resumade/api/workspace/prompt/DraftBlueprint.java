package com.resumade.api.workspace.prompt;

import java.util.List;

public record DraftBlueprint(
        int targetLength,
        int paragraphCount,
        List<ParagraphPlan> paragraphs
) {
    public DraftBlueprint {
        paragraphs = paragraphs == null ? List.of() : List.copyOf(paragraphs);
    }

    public record ParagraphPlan(
            int paragraph,
            String role,
            List<String> units,
            int targetChars
    ) {
        public ParagraphPlan {
            role = role == null ? "" : role.trim();
            units = units == null ? List.of() : List.copyOf(units);
        }
    }
}
