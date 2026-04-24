package com.resumade.api.workspace.prompt;

import java.util.List;

/**
 * 자기소개서 초안 생성에 필요한 모든 파라미터를 담는 Value Object.
 *
 * <p>WorkspaceService → PromptFactory → PromptStrategy 흐름에서
 * 파라미터 폭발(parameter explosion) 문제를 해결합니다.
 */
public record DraftParams(
        /** 지원 기업명 */
        String company,

        /** 지원 직무명 */
        String position,

        /** 현재 문항 제목 */
        String questionTitle,

        /** 기업 리서치 + JD 인사이트 + rawJD 조합 컨텍스트 */
        String companyContext,

        /** 최대 허용 글자 수 (Hard Limit) */
        int maxLength,

        /** 목표 최소 글자 수 */
        int minTarget,

        /** 목표 최대 글자 수 */
        int maxTarget,

        /** RAG 검색 결과를 직렬화한 경험 컨텍스트 문자열 */
        String experienceContext,

        /** 다른 문항 초안 요약 (중복 방지용) */
        String othersContext,

        /** 사용자 추가 지시사항 (배치 전략 + 개인 directive 병합본) */
        String directive,

        String draftPlanContext,

        /**
         * 성장과정 라이프스토리와 별도로 전달되는 작성 가이드라인.
         * 강조 역량·성장 흐름·문체 지침·금지 항목 등을 포함.
         * PersonalGrowth 문항 전용. 다른 카테고리에서는 null.
         */
        String writingGuideContext,

        /**
         * 복합 문항에서 추출된 세부 요구 항목 목록.
         * 단순 문항이면 null 또는 빈 리스트. PromptFactory가
         * {@code <Additional_Requirements>} 블록으로 주입합니다.
         */
        List<String> additionalIntents
) {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String company;
        private String position;
        private String questionTitle;
        private String companyContext;
        private int maxLength;
        private int minTarget;
        private int maxTarget;
        private String experienceContext;
        private String othersContext;
        private String directive;
        private String draftPlanContext;
        private String writingGuideContext;
        private List<String> additionalIntents;

        public Builder company(String company)                           { this.company = company; return this; }
        public Builder position(String position)                         { this.position = position; return this; }
        public Builder questionTitle(String questionTitle)               { this.questionTitle = questionTitle; return this; }
        public Builder companyContext(String companyContext)             { this.companyContext = companyContext; return this; }
        public Builder maxLength(int maxLength)                          { this.maxLength = maxLength; return this; }
        public Builder minTarget(int minTarget)                          { this.minTarget = minTarget; return this; }
        public Builder maxTarget(int maxTarget)                          { this.maxTarget = maxTarget; return this; }
        public Builder experienceContext(String ctx)                     { this.experienceContext = ctx; return this; }
        public Builder othersContext(String othersContext)               { this.othersContext = othersContext; return this; }
        public Builder directive(String directive)                       { this.directive = directive; return this; }
        public Builder draftPlanContext(String draftPlanContext)         { this.draftPlanContext = draftPlanContext; return this; }
        public Builder writingGuideContext(String writingGuideContext)   { this.writingGuideContext = writingGuideContext; return this; }
        public Builder additionalIntents(List<String> additionalIntents) { this.additionalIntents = additionalIntents; return this; }

        public DraftParams build() {
            return new DraftParams(
                    company, position, questionTitle, companyContext,
                    maxLength, minTarget, maxTarget,
                    experienceContext, othersContext, directive, draftPlanContext, writingGuideContext,
                    additionalIntents
            );
        }
    }
}
