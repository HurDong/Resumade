package com.resumade.api.workspace.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 생성된 초안이 requiredElements를 충분히 커버하는지 검사하는 경량 LLM 서비스 (Tier-2).
 *
 * <p>반환값 예시 (JSON):
 * <pre>
 * {
 *   "passed": false,
 *   "missingElements": ["가족사항과의 연결", "교우관계에서 형성된 역량"],
 *   "feedback": "가족사항 언급이 1문장에 그쳐 구체적 연결이 부족합니다."
 * }
 * </pre>
 */
public interface DraftQualityCheckerAiService {

    @SystemMessage("""
            당신은 자기소개서 초안 검수 전문가입니다.
            주어진 초안이 필수 요소 목록을 충분히 다루고 있는지 판단하세요.

            [판단 기준]
            - "충분히 다룸": 해당 요소가 1개 이상의 구체적 문장으로 서사 안에 통합되어 있음
            - "부족함": 언급이 없거나 1~2어절 수준의 형식적 언급만 있는 경우

            [응답 형식] JSON만 반환, 마크다운 코드 펜스 금지
            {
              "passed": true/false,
              "missingElements": ["부족한 요소1", "부족한 요소2"],
              "feedback": "전체 피드백 1~2문장"
            }
            - passed=true면 missingElements는 빈 배열, feedback은 빈 문자열
            """)
    @UserMessage("""
            [필수 요소 목록]
            {{requiredElements}}

            [검사할 초안]
            {{draft}}
            """)
    String check(@V("requiredElements") String requiredElements, @V("draft") String draft);
}
