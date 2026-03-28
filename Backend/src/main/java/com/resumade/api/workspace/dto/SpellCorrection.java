package com.resumade.api.workspace.dto;

/**
 * 단일 맞춤법 교정 제안.
 *
 * @param errorWord     원문에 실제로 존재하는 오류 어절/문자열 (프론트엔드 하이라이팅 키)
 * @param suggestedWord 교정된 어절/문자열
 * @param reason        교정 사유 — "맞춤법 오류" | "띄어쓰기 오류" | "어미 오류" | "조사 오류"
 */
public record SpellCorrection(
        String errorWord,
        String suggestedWord,
        String reason
) {}
