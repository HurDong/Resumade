package com.resumade.api.workspace.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApplicationStatus {
    DOCUMENT("document", "서류 전형"),
    APTITUDE("aptitude", "인적성/코딩테스트"),
    INTERVIEW1("interview1", "1차 면접"),
    INTERVIEW2("interview2", "2차 면접"),
    PASSED("passed", "최종 합격");

    private final String id;
    private final String title;

    public static ApplicationStatus fromId(String id) {
        if (id == null) return DOCUMENT;
        for (ApplicationStatus status : values()) {
            if (status.id.equalsIgnoreCase(id)) {
                return status;
            }
        }
        return DOCUMENT; // Default
    }
}
