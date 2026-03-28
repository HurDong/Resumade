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
            "직무 관련 프로젝트 경험, 기술 스택 활용, 구체적 성과 및 결과, 역할과 기여도와 관련된 문항",
            List.of("프로젝트", "경험", "기술", "개발", "성과", "구현", "역할", "기여")
    ),

    /**
     * 문제 해결, 도전 극복, 실패/위기 대처
     * → RAG: description, rawContent 부스팅 (문제-해결 서사 중심)
     */
    PROBLEM_SOLVING(
            "문제 해결 및 도전",
            "어려움, 실패, 위기 상황 극복, 문제 해결 과정, 도전 경험과 관련된 문항",
            List.of("문제해결", "도전", "실패", "극복", "어려움", "위기", "개선", "해결")
    ),

    /**
     * 팀워크, 협업, 갈등 해결, 리더십
     * → RAG: role, description 부스팅 (팀 맥락 중심)
     */
    COLLABORATION(
            "협업 및 리더십",
            "팀워크, 협업 경험, 갈등 해결, 의사소통, 리더십 발휘와 관련된 문항",
            List.of("협업", "팀워크", "리더십", "소통", "갈등", "팀", "주도", "조율")
    ),

    /**
     * 성장 경험, 자기 개발, 피드백 수용, 배움
     * → RAG: description, rawContent 부스팅 (변화/학습 서사 중심)
     */
    GROWTH(
            "성장 및 자기 개발",
            "성장 경험, 자기 개발 노력, 피드백 수용, 새로운 기술 습득, 변화와 관련된 문항",
            List.of("성장", "학습", "개발", "습득", "노력", "발전", "피드백", "개선")
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
