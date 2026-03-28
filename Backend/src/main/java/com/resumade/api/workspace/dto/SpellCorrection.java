package com.resumade.api.workspace.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 단일 맞춤법 교정 제안.
 *
 * <p>LangChain4j 0.31.0 PojoOutputParser 호환을 위해 Record 대신 일반 클래스 사용.
 * Jackson은 no-arg 생성자 + setter 기반으로 역직렬화한다.</p>
 */
@Getter
@Setter
@NoArgsConstructor
public class SpellCorrection {

    /** 원문에 실제로 존재하는 오류 어절/문자열 (프론트엔드 하이라이팅 키) */
    private String errorWord;

    /** 교정된 어절/문자열 */
    private String suggestedWord;

    /** 교정 사유 — "맞춤법 오류" | "띄어쓰기 오류" | "어미 오류" | "조사 오류" */
    private String reason;
}
