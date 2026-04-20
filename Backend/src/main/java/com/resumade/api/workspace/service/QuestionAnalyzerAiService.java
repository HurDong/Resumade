package com.resumade.api.workspace.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 자기소개서 문항을 심층 분석하여 QuestionProfile 생성에 필요한 JSON을 반환하는 LLM 서비스.
 *
 * <p>기존 ClassifierAiService + IntentExtractorAiService를 하나로 통합합니다.
 * 카테고리 분류 + 복합 여부 + 서사 전략(framingNote) + RAG 키워드를 한 번의 호출로 처리합니다.
 *
 * <p>반환값 예시:
 * <pre>
 * {
 *   "primaryCategory": "MOTIVATION",
 *   "isCompound": true,
 *   "framingNote": "...",
 *   "requiredElements": ["지원동기", "가족사항과 형성된 가치관", "교우관계 역량 연결"],
 *   "ragKeywords": ["성장과정", "가치관 형성", "지원 이유"]
 * }
 * </pre>
 */
public interface QuestionAnalyzerAiService {

    @SystemMessage("""
            당신은 한국 채용 자기소개서 문항을 분석하는 전문가입니다.
            주어진 문항을 분석하여 아래 JSON 형식으로만 응답하세요. JSON 외 어떤 텍스트도 금지.

            [카테고리 옵션]
            - MOTIVATION: 지원동기, 입사 이유, 회사/직무 선택 이유, 비전, 포부
            - EXPERIENCE: 직무 관련 프로젝트·기술 경험, 역할, 성과, 구현 과정
            - PROBLEM_SOLVING: 문제 상황, 근본 원인 분석, 극복/해결 과정, 실패 경험
            - COLLABORATION: 팀워크, 협업, 갈등 해결, 리더십, 소통 방식
            - PERSONAL_GROWTH: 성장과정, 가치관 형성, 인성, 삶의 서사, 가족사항, 생활습관
            - CULTURE_FIT: 조직문화 적합성, 핵심가치, 장단점, 일하는 방식, 성격
            - TREND_INSIGHT: 기술·산업·사회 이슈 분석, 시장 변화, 인사이트
            - DEFAULT: 위 카테고리로 분류 불가한 경우

            [isCompound 판단 기준]
            아래 중 하나라도 해당하면 true:
            - 문항이 2개 이상의 다른 요구를 포함 (예: "지원동기와 입사 후 목표를 서술")
            - 문항에 비표준 조건이 붙음 (예: "가족사항·교우관계와 연계하여", "본인의 경험과 회사 비전을 연결하여")
            - 카테고리 외 추가 요구가 존재 (예: MOTIVATION이지만 자기소개 요소 포함)

            [framingNote 작성 규칙]
            - isCompound=false면 반드시 null
            - isCompound=true면: 이 문항을 어떻게 구조화해야 하는지 서사 흐름을 한국어로 2~4문장으로 설명
              예) "가족사항에서 형성된 가치관이 교우관계를 통해 강화되고, 그것이 이 직무 선택으로 자연스럽게 이어지도록 서술. 결론형 오프닝 대신 개인 서사 → 가치관 확립 → 직무 연결 순으로 전개."
            - 기본 카테고리 구조를 그대로 따르면 되는 경우에도 isCompound=true이면 framingNote를 반드시 제공

            [requiredElements 규칙]
            - isCompound=false면 빈 배열 []
            - isCompound=true면: 문항이 요구하는 각 항목을 20자 이내 한국어 명사구로 열거 (2~5개)

            [ragKeywords 규칙]
            - 경험 볼트(Elasticsearch)에서 관련 경험을 찾기 위한 검색 키워드
            - 문항 주제와 카테고리에서 도출, 2~5개 한국어 키워드

            [응답 형식]
            {
              "primaryCategory": "카테고리명",
              "isCompound": true/false,
              "framingNote": "서사 전략 설명 또는 null",
              "requiredElements": ["항목1", "항목2"],
              "ragKeywords": ["키워드1", "키워드2"]
            }
            """)
    @UserMessage("문항: {{question}}")
    String analyze(@V("question") String question);
}
