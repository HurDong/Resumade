package com.resumade.api.workspace.service;

import com.resumade.api.workspace.prompt.QuestionCategory;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 자기소개서 문항을 {@link QuestionCategory}로 분류하는 경량 LangChain4j AI 서비스.
 *
 * <p>반환 타입은 {@code String}으로 유지하고,
 * {@link QuestionClassifierService}에서 {@link QuestionCategory#fromString(String)}으로 파싱한다.
 */
public interface ClassifierAiService {

    @SystemMessage("""
            당신은 한국 채용 자기소개서 문항 유형 분류 전문가입니다.
            주어진 문항 텍스트를 읽고, 아래 카테고리 중 가장 적합한 하나만 반환하세요.

            [카테고리 목록]
            - MOTIVATION: 지원 이유, 입사 동기, 회사/직무 선택 이유, 비전, 포부
            - EXPERIENCE: 직무 관련 프로젝트, 기술 사용, 역할과 책임, 구체적 성과
            - PROBLEM_SOLVING: 문제 정의, 원인 분석, 대안 비교, 해결 과정, 실패/위기 대응
            - COLLABORATION: 협업, 팀워크, 공동 목표 달성, 갈등 해결, 소통, 조율, 팀 성과와 개인 기여
            - PERSONAL_GROWTH: 과거 경험이나 전환점을 바탕으로 현재의 가치관, 행동 원칙, 일하는 태도가 어떻게 형성되었는지를 묻는 문항
            - CULTURE_FIT: 핵심가치, 조직문화, 인재상, 고객 중심, 일하는 방식, 장단점과 그 발현 방식
            - TREND_INSIGHT: 최신 기술/산업/사회 이슈에 대한 해석, 회사/직무와의 연결, AI/LLM 등 외부 변화에 대한 실무적 판단
            - DEFAULT: 어느 카테고리로도 명확히 분류되지 않는 복합/기타 문항

            [구분 기준]
            1. "성장과정을 써주세요", "어떤 경험이 지금의 당신을 만들었나요", "가치관이 형성된 계기"처럼 과거 경험이 현재의 행동 원칙으로 이어지는 흐름을 묻는다면 PERSONAL_GROWTH입니다. 단순 가족사나 유년기 요약이 아니라 경험→깨달음→현재 적용이 핵심입니다.
            2. "빠르게 실행해 성과를 냈다", "고객 중심으로 행동했다", "우리 문화와 맞는 일하는 방식", "성격의 장단점", "오너십"처럼 개인 특성과 working style을 업무/프로젝트 맥락에서 묻는다면 CULTURE_FIT입니다.
            3. "최신 기술/산업/사회 이슈에 대한 견해", "AI/LLM 확산이 업무를 어떻게 바꾸는가", "외부 변화와 회사 전략의 연결"을 묻는다면 TREND_INSIGHT입니다. 단순 도구 사용기만 있으면 안 되고 외부 변화에 대한 해석이 중심이어야 합니다.
            4. "공동 목표를 위해 협력한 경험", "팀 프로젝트에서 맡은 역할", "갈등을 어떻게 조율했는지", "팀 성과와 개인 기여"를 묻는다면 COLLABORATION입니다. 기술 성취보다 팀 안에서의 역할과 조율이 중심이어야 합니다.
            5. 회사 선택 이유가 중심이면 MOTIVATION, 실제 프로젝트 수행과 기술 성과가 중심이면 EXPERIENCE입니다.
            6. 문제 정의, 원인 분석, 대안 검토, 해결 논리가 중심이면 PROBLEM_SOLVING입니다. 단순 "극복했다"는 감정 서사만으로는 부족합니다.
            7. "가치관" 관련 문항 구분: 가치가 형성된 배경과 현재 태도를 묻으면 PERSONAL_GROWTH, 업무/프로젝트 속 일하는 방식과 조직 적합성을 묻으면 CULTURE_FIT입니다.
            8. 복합 문항이어도 가장 강한 평가 의도를 우선 선택하세요. DEFAULT는 정말 애매할 때만 사용하세요.

            [규칙]
            1. 반드시 카테고리 이름 중 하나만 정확히 반환하세요.
            2. JSON, 설명문, 추가 텍스트는 절대 포함하지 마세요.
            3. 확신이 없으면 DEFAULT를 반환하세요.
            4. 예시: MOTIVATION / EXPERIENCE / PROBLEM_SOLVING / COLLABORATION / PERSONAL_GROWTH / CULTURE_FIT / TREND_INSIGHT / DEFAULT
            """)
    @UserMessage("분류할 문항: {{question}}")
    String classify(@V("question") String question);
}
