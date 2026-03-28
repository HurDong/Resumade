package com.resumade.api.workspace.service;

import com.resumade.api.workspace.prompt.QuestionCategory;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 자기소개서 문항을 {@link QuestionCategory}로 분류하는 경량 LangChain4j AI 서비스.
 *
 * <p>반환 타입은 {@code String}으로 선언하고,
 * {@link QuestionClassifierService}에서 {@link QuestionCategory#fromString(String)}으로 파싱합니다.
 * 이유: LangChain4j 0.31.x는 Enum 직접 반환을 지원하지 않습니다.
 *
 * <p><b>비용 최적화:</b> gpt-4o-mini (소형 모델) 전용으로 바인딩됩니다.
 * AiConfig에서 {@code classifierAiService} 빈으로 등록하세요.
 */
public interface ClassifierAiService {

    @SystemMessage("""
            당신은 한국어 자기소개서 문항 유형 분류 전문가입니다.
            주어진 문항 텍스트를 읽고, 아래 카테고리 중 가장 적합한 하나를 반환하세요.

            [카테고리 목록]
            - MOTIVATION: 지원 이유, 입사 동기, 회사/직무 선택 이유, 비전, 포부
            - EXPERIENCE: 직무 관련 프로젝트, 기술 스택 활용, 구체적 성과, 역할과 기여도
            - PROBLEM_SOLVING: 어려움 극복, 실패 경험, 문제 해결 과정, 도전 사례
            - COLLABORATION: 팀워크, 협업, 갈등 해결, 의사소통, 리더십
            - GROWTH: 성장 경험, 자기 개발, 피드백 수용, 새로운 기술/역량 습득
            - DEFAULT: 위 어느 카테고리에도 명확히 해당하지 않는 복합/기타 문항

            [규칙]
            1. 반드시 위 카테고리 이름 중 정확히 하나만 반환하세요.
            2. JSON, 설명문, 추가 텍스트를 절대 포함하지 마세요.
            3. 확신이 없으면 DEFAULT를 반환하세요.
            4. 예시: MOTIVATION / EXPERIENCE / PROBLEM_SOLVING / COLLABORATION / GROWTH / DEFAULT
            """)
    @UserMessage("분류할 문항: {{question}}")
    String classify(@V("question") String question);
}
