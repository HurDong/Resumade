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
            - PERSONAL_GROWTH: 어린 시절·가정 환경·인생 전환점 등 삶의 서사를 통해 지원자의 가치관, 인성, 삶의 태도가 형성된 과정을 묻는 문항
            - CULTURE_FIT: 빠른 실행, MVP, 고객 중심, 실험 문화, 오너십, 회사의 일하는 방식과의 적합성, 성격의 장단점, 자신의 가치관이나 일하는 스타일
            - TREND_INSIGHT: 최근 기술/산업/사회 이슈에 대한 견해, 비즈니스 영향 해석, 회사/직무와의 연결, AI/LLM 도구 활용 경험이나 견해
            - DEFAULT: 위 어느 카테고리에도 명확히 해당하지 않는 복합/기타 문항

            [구분 기준]
            1. "성장과정을 써주세요", "어떤 경험이 지금의 당신을 만들었나요" 같은 문항이면 PERSONAL_GROWTH입니다. 프로젝트나 기술지식이 아닌 인생 이야기가 중요한 문항입니다.
            2. "빠르게 실행해 성과를 냈다", "고객 반응으로 검증했다", "조직 문화와 맞는 일하는 방식"을 묻는다면 CULTURE_FIT입니다. 또한 "성격의 장단점", "자신의 강점과 약점", "일하는 스타일" 등 개인 특성을 프로젝트 맥락에서 묻는 문항도 CULTURE_FIT입니다.
            3. "최근 기술/산업/사회 이슈에 대한 견해"를 묻는다면 TREND_INSIGHT입니다. "AI/LLM 도구를 어떻게 활용하는지", "새로운 기술에 대한 경험" 등도 TREND_INSIGHT입니다.
            4. 회사 선택 이유가 중심이면 MOTIVATION, 실제 프로젝트 수행과 기술 성과가 중심이면 EXPERIENCE입니다.
            5. 복합 문항이어도 가장 강한 평가 의도를 우선 선택하세요. DEFAULT는 정말 애매할 때만 사용하세요.
            6. "가치관" 관련 문항 구분: 삶의 서사(어린 시절, 성장 배경)를 통해 묻는다면 PERSONAL_GROWTH, 업무/프로젝트 맥락에서 묻는다면 CULTURE_FIT입니다.

            [규칙]
            1. 반드시 위 카테고리 이름 중 정확히 하나만 반환하세요.
            2. JSON, 설명문, 추가 텍스트를 절대 포함하지 마세요.
            3. 확신이 없으면 DEFAULT를 반환하세요.
            4. 예시: MOTIVATION / EXPERIENCE / PROBLEM_SOLVING / COLLABORATION / PERSONAL_GROWTH / CULTURE_FIT / TREND_INSIGHT / DEFAULT
            """)
    @UserMessage("분류할 문항: {{question}}")
    String classify(@V("question") String question);
}
