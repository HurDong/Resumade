package com.resumade.api.workspace.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyResearchResponse {

    private Focus focus;
    private String executiveSummary;

    // ── 기존 섹션 (하위 호환 유지) ─────────────────────────────────
    private List<String> businessContext;
    private List<String> serviceLandscape;
    private List<String> roleScope;
    private List<String> motivationHooks;
    private List<String> serviceHooks;
    private List<String> resumeAngles;
    private List<String> interviewSignals;
    private String recommendedNarrative;
    private List<String> followUpQuestions;
    private List<String> confidenceNotes;

    // ── 신규 구조화 섹션 ────────────────────────────────────────────
    /** AI가 JD + 웹 검색으로 자동 발견한 맥락 */
    private DiscoveredContext discoveredContext;

    /** 구조화된 기술 스택 (confidence + source 포함) */
    private List<TechStackItem> techStack;

    /** 팩트/수치 기반 최근 기술 작업 */
    private List<TechFact> recentTechWork;

    /** JD 명시 요구사항 vs 실제 파악된 기술 갭 분석 */
    private FitAnalysis fitAnalysis;

    /** Gemini가 실제로 참고한 웹 출처 (groundingMetadata) */
    private List<String> searchQueries;
    private List<SearchSource> searchSources;

    // ── Inner Classes ───────────────────────────────────────────────

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Focus {
        private String company;
        private String position;
        /** AI가 추론한 사업부/팀 */
        private String inferredBusinessUnit;
        /** AI가 추론한 제품/서비스 */
        private String inferredProduct;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DiscoveredContext {
        /** 추론된 사업부/팀 */
        private String businessUnit;
        /** 해당 팀이 만드는 제품/서비스 */
        private String product;
        /** 정보 출처 목록 (예: "원티드 경력공고 2025.03", "공식 테크블로그") */
        private List<String> evidenceSources;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TechStackItem {
        /** 기술명 (예: "Java 17", "Spring Boot 3.2") */
        private String name;
        /** 카테고리: Backend / Frontend / Database / Infrastructure / DevOps / Mobile / AI-ML */
        private String category;
        /** 확신도: CONFIRMED / INFERRED / UNCERTAIN */
        private String confidence;
        /** 출처 (예: "원티드 경력공고", "공식 테크블로그", "GitHub 레포") */
        private String source;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TechFact {
        /** 한 줄 요약 */
        private String summary;
        /** 구체적인 수치/기술 상세 내용 */
        private String detail;
        /** 출처 */
        private String source;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class SearchSource {
        /** 페이지 제목 */
        private String title;
        /** 실제 URL */
        private String uri;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class FitAnalysis {
        /** JD에 명시된 요구사항 목록 */
        private List<String> jdStatedRequirements;
        /** 실제 파악된 기술 스택 목록 */
        private List<String> actualTechStack;
        /** JD 명시 vs 실제 갭 분석 (서술형) */
        private String gapAnalysis;
        /** 자소서에서 강조할 포인트 */
        private List<String> coverLetterHints;
    }
}
