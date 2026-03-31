package com.resumade.api.workspace.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 부산대학교 맞춤법 검사기 API 응답 DTO.
 *
 * <p>단일 청크(≤500자) 호출 결과를 담는다. 전체 텍스트가 500자를 초과하면
 * {@link com.resumade.api.workspace.service.PnuSpellCheckClient}가
 * 여러 청크로 나눠 호출한 뒤 병합한다.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PnuApiResponse {

    @JsonProperty("errInfo")
    private List<ErrInfo> errInfo;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ErrInfo {

        /** 원문에서 틀린 어절/문자열 */
        @JsonProperty("orgStr")
        private String orgStr;

        /**
         * 교정 후보 — 복수일 경우 {@code |} 구분자로 연결됨.
         * 첫 번째 후보를 suggestedWord로 사용한다.
         */
        @JsonProperty("candWord")
        private String candWord;

        /** 교정 설명 */
        @JsonProperty("help")
        private String help;

        /**
         * 오류 유형 코드.
         * <ul>
         *   <li>1 : 맞춤법 오류</li>
         *   <li>2 : 띄어쓰기 오류</li>
         *   <li>3 : 표준어 의심</li>
         *   <li>4 : 통계적 교정</li>
         * </ul>
         */
        @JsonProperty("type")
        private int type;
    }
}
