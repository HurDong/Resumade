package com.resumade.api.workspace.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 맞춤법 검사 API 응답.
 *
 * <p>LangChain4j 0.31.0 PojoOutputParser 호환을 위해 Record 대신 일반 클래스 사용.
 * PojoOutputParser가 public getter를 통해 필드 스키마를 인식하므로
 * {@code @Getter} + no-arg 생성자 조합이 필수다.</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class SpellCheckResponse {

    /** 교정 제안 목록 (LLM이 오류를 찾지 못하면 빈 배열) */
    private List<SpellCorrection> corrections;

    /** 오류 없음 응답 — LLM 호출 실패 시 안전 폴백으로도 사용 */
    public static SpellCheckResponse empty() {
        SpellCheckResponse r = new SpellCheckResponse();
        r.corrections = List.of();
        return r;
    }
}
