package com.resumade.api.profile.domain;

import java.util.Arrays;

public enum ProfileCategory {
    CERTIFICATION("certification"),
    LANGUAGE("language"),
    AWARD("award"),
    EDUCATION("education"),
    SKILL("skill");

    private final String value;

    ProfileCategory(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ProfileCategory from(String value) {
        return Arrays.stream(values())
                .filter(category -> category.value.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported profile category: " + value));
    }
}
