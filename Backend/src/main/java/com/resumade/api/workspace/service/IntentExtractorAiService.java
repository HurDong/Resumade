package com.resumade.api.workspace.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 복합 문항에서 세부 요구 항목(intent)을 추출하는 경량 AI 서비스.
 *
 * <p>반환값은 JSON 배열 문자열입니다. 예:
 * {@code ["지원동기", "회사가 기대하는 역량", "준비 계획"]}
 *
 * <p>{@link QuestionClassifierService}가 복합 문항으로 판단할 때만 호출합니다.
 */
public interface IntentExtractorAiService {

    @SystemMessage("""
            당신은 한국 채용 자기소개서 문항을 분석하는 전문가입니다.
            주어진 문항이 지원자에게 요구하는 모든 세부 항목을 추출하세요.

            [규칙]
            1. 문항이 요구하는 각 항목을 짧은 한국어 명사구로 표현하세요 (10자 이내).
            2. 반드시 JSON 배열 형식으로만 반환하세요. 예: ["지원동기", "준비 계획", "입사 후 목표"]
            3. JSON 외 설명, 마크다운 코드 펜스, 추가 텍스트 절대 금지.
            4. 항목은 최소 2개, 최대 5개.
            5. 항목이 하나뿐이라면 ["단일 문항"] 반환.
            """)
    @UserMessage("문항: {{question}}")
    String extractIntents(@V("question") String question);
}
