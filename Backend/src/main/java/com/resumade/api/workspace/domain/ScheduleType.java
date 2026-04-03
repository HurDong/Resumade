package com.resumade.api.workspace.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ScheduleType {

    DOCUMENT_DEADLINE("서류 마감",    "deadline"),
    CODING_TEST       ("코딩테스트",  "codingTest"),
    APTITUDE          ("인적성검사",  "aptitude"),
    INTERVIEW1        ("1차 면접",    "interview"),
    INTERVIEW2        ("2차 면접",    "interview"),
    CUSTOM            ("커스텀",      "interview");  // 커피챗, 과제전형 등 사용자 정의

    /** UI에 표시할 기본 레이블 (CUSTOM은 customLabel로 덮어씀) */
    private final String defaultLabel;

    /** 프론트엔드 CalendarEventType으로 매핑될 문자열 */
    private final String calendarEventType;
}
