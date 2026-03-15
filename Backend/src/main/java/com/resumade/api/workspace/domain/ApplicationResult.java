package com.resumade.api.workspace.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ApplicationResult {
    PENDING("pending", "진행 중"),
    PASS("pass", "합격"),
    FAIL("fail", "불합격");

    private final String id;
    private final String title;

    public static ApplicationResult fromId(String id) {
        if (id == null) return PENDING;
        for (ApplicationResult result : values()) {
            if (statusMatch(result, id)) {
                return result;
            }
        }
        return PENDING;
    }

    private static boolean statusMatch(ApplicationResult result, String id) {
        return result.id.equalsIgnoreCase(id) || result.name().equalsIgnoreCase(id);
    }
}
