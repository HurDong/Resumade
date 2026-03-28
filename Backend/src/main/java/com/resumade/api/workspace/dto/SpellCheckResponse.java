package com.resumade.api.workspace.dto;

import java.util.List;

/**
 * 맞춤법 검사 API 응답.
 * 교정 제안이 없으면 corrections 는 빈 리스트다.
 *
 * @param corrections 교정 제안 목록 (LLM 이 오류를 찾지 못하면 빈 배열)
 */
public record SpellCheckResponse(
        List<SpellCorrection> corrections
) {
    /** 오류 없음 응답 — LLM 호출 실패 시 안전 폴백으로도 사용 */
    public static SpellCheckResponse empty() {
        return new SpellCheckResponse(List.of());
    }
}
