package com.resumade.api.workspace.prompt;

import java.util.Arrays;
import java.util.List;

/**
 * 자기소개서 문항 유형 분류 Enum.
 *
 * <p>분류기(Classifier)가 반환하는 카테고리로, 각 카테고리는:
 * <ul>
 *   <li>category-specific 프롬프트 전략 선택 (PromptFactory)</li>
 *   <li>Elasticsearch 키워드 부스팅 필드 결정 (ExperienceVectorRetrievalService)</li>
 * </ul>
 * 두 가지 목적으로 사용됩니다.
 */
public enum QuestionCategory {

    /**
     * 지원 동기, 입사 이유, 해당 기업/직무 선택 이유
     * → RAG: description, rawContent 부스팅 (서사/맥락 중심)
     */
    MOTIVATION(
            "지원동기 및 목표",
            "지원 이유, 입사 동기, 회사/직무 선택 이유, 비전, 포부, 이루고 싶은 목표와 관련된 문항",
            List.of("지원동기", "입사동기", "지원이유", "목표", "비전", "포부", "성장목표")
    ),

    /**
     * 직무 관련 프로젝트 경험, 기술적 성취, 구체적 역할 및 결과
     * → RAG: techStack, metrics, role 부스팅 (기술/정량 중심)
     */
    EXPERIENCE(
            "직무 경험 및 성과",
            "직무 관련 프로젝트 경험, 실제 역할과 책임, 기술 선택 근거, 문제 해결 과정, 구체적 성과 및 결과를 묻는 문항. 지원 동기나 가치관보다 직무 수행 역량 증명이 중심",
            List.of("프로젝트", "경험", "기술", "개발", "성과", "구현", "역할", "기여")
    ),

    /**
     * 문제 해결, 도전 극복, 실패/위기 대처
     * → RAG: description, rawContent 부스팅 (문제-해결 서사 중심)
     */
    PROBLEM_SOLVING(
            "문제 해결 및 도전",
            "구체적 문제 상황, 근본 원인 분석, 대안 검토, 선택 기준, 제약 조건, 해결 과정과 관련된 문항. 단순한 도전 서사보다 문제 해결의 논리와 실행 판단이 중심",
            List.of("문제해결", "도전", "실패", "극복", "어려움", "위기", "원인", "진단", "분석", "대안", "개선", "해결")
    ),

    /**
     * 팀워크, 협업, 갈등 해결, 리더십
     * → RAG: role, description 부스팅 (팀 맥락 중심)
     */
    COLLABORATION(
            "협업 및 리더십",
            "공동 목표 아래에서의 역할 분담, 팀워크, 협업 과정, 갈등 해결, 의사소통, 조율 방식, 팀 성과와 개인 기여를 묻는 문항. 개인 성취보다 조직 내 상호작용이 중심",
            List.of("협업", "팀워크", "공동목표", "리더십", "소통", "갈등", "팀", "주도", "조율", "기여", "역할분담")
    ),

    /**
     * 성장과정, 가치관 형성, 인성/삶의 서사 중심 문항
     * → Personal Story Vault 기반 (RAG 우회 대상)
     * 예: "어떤 경험이 지금의 당신을 만들었나요?", "성장과정에서 중요한 가치관은?"
     */
    PERSONAL_GROWTH(
            "성장과정 및 가치관",
            "과거 경험이나 전환점을 바탕으로 현재의 행동 원칙, 가치관, 일하는 태도가 어떻게 형성되었는지를 묻는 문항. 단순 유년기/가족사 나열이 아니라 경험→깨달음→현재 적용이 중심",
            List.of("성장과정", "가치관", "전환점", "형성", "원칙", "태도", "배경", "계기", "신념", "변화", "배움", "어린시절")
    ),

    /**
     * 조직 문화 적합성, 핵심가치, 장단점, 고객 중심, 오너십, 일하는 방식
     * → RAG: description, role, rawContent 부스팅 (행동/태도/팀 영향 서사 중심)
     */
    CULTURE_FIT(
            "조직문화·실행력·성격 적합성",
            "회사 핵심가치, 조직문화, 인재상, 고객 중심, 오너십, 소통 방식, 빠른 실행과 같은 일하는 방식과의 적합성을 묻는 문항. 성격의 장단점, 자신의 가치관이나 일하는 스타일을 업무/프로젝트 맥락에서 묻는 문항도 포함",
            List.of("조직문화", "문화", "가치", "인재상", "고객", "고객중심", "오너십", "소통", "실행", "속도", "성격", "장단점", "강점", "약점", "가치관", "스타일")
    ),

    /**
     * 기술/산업/사회 이슈에 대한 인사이트와 기업 적용 관점
     * → RAG: description, techStack, rawContent 부스팅 (이슈-해석-기여 연결 중심)
     */
    TREND_INSIGHT(
            "기술/산업 인사이트",
            "최근 기술 동향, 산업/사회 이슈, 규제 변화, 시장 변화에 대한 분석과 이를 회사/직무와 연결하는 문항. 개인 경험은 보조 근거로 쓰이며, AI/LLM 도구 활용은 단순 사용기가 아니라 업무 변화나 적용 판단을 묻는 경우에 한해 포함",
            List.of("이슈", "트렌드", "동향", "산업", "시장", "정책", "규제", "AI", "클라우드", "보안", "플랫폼", "비즈니스", "견해", "LLM", "ChatGPT", "생성형")
    ),

    /**
     * 분류 불가 또는 복합 유형 — 기본 전략 적용
     */
    DEFAULT(
            "기타",
            "명확한 유형으로 분류하기 어려운 복합 문항 또는 일반 문항",
            List.of()
    );

    /** UI 표시용 한국어 이름 */
    private final String displayName;

    /** 분류기 프롬프트에 포함될 카테고리 설명 */
    private final String descriptionForClassifier;

    /** Elasticsearch 키워드 검색에서 우선 매칭할 관련 태그 (한국어) */
    private final List<String> relatedTags;

    QuestionCategory(String displayName, String descriptionForClassifier, List<String> relatedTags) {
        this.displayName = displayName;
        this.descriptionForClassifier = descriptionForClassifier;
        this.relatedTags = relatedTags;
    }

    public String getDisplayName()                { return displayName; }
    public String getDescriptionForClassifier()   { return descriptionForClassifier; }
    public List<String> getRelatedTags()          { return relatedTags; }

    /**
     * LLM 응답 문자열을 안전하게 파싱. 파싱 실패 시 {@code DEFAULT} 반환.
     */
    public static QuestionCategory fromString(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        String normalized = value.trim().toUpperCase()
                .replaceAll("[^A-Z_]", "");
        return Arrays.stream(values())
                .filter(c -> c.name().equals(normalized))
                .findFirst()
                .orElse(DEFAULT);
    }

    /**
     * 분류기 프롬프트 구성용 — 모든 카테고리 옵션을 번호 목록 문자열로 반환
     */
    public static String buildClassifierOptions() {
        StringBuilder sb = new StringBuilder();
        for (QuestionCategory category : values()) {
            if (category == DEFAULT) continue;
            sb.append("- ").append(category.name())
              .append(": ").append(category.descriptionForClassifier)
              .append("\n");
        }
        sb.append("- DEFAULT: 위 어느 카테고리에도 명확히 해당하지 않는 경우");
        return sb.toString();
    }
}
