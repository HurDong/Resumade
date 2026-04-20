package com.resumade.api.workspace.prompt;

import java.util.List;

/**
 * 문항 분석 결과 VO. QuestionAnalysisService가 생성하며
 * v2 파이프라인 전체에서 카테고리·전략·RAG 키워드의 단일 출처로 사용됩니다.
 *
 * <p>단순 문항이면 framingNote는 null이고 requiredElements는 빈 리스트입니다.
 * 복합/특수 문항이면 framingNote가 답변 구조 전략을 담고
 * requiredElements가 커버 체크리스트를 담습니다.
 */
public record QuestionProfile(

        /** 주 카테고리 — PromptFactory의 전략 선택 및 Elasticsearch 부스팅에 사용 */
        QuestionCategory primaryCategory,

        /** true면 문항이 여러 asks를 포함하거나 비표준 조건을 가짐 */
        boolean isCompound,

        /**
         * 이 문항을 어떻게 풀어야 하는지에 대한 전략적 방향.
         * 단순 문항이면 null.
         * 복합/특수 문항이면 기본 Draft_Structure를 오버라이드하는 서사 흐름 지침.
         */
        String framingNote,

        /**
         * 반드시 커버해야 할 요소 목록. DraftQualityCheckService의 Tier-2 검수에도 사용.
         * 단순 문항이면 빈 리스트.
         */
        List<String> requiredElements,

        /**
         * Elasticsearch 검색에 활용할 키워드 힌트.
         * QuestionAnalyzer가 문항과 카테고리에서 도출.
         */
        List<String> ragKeywords

) {
    public static QuestionProfile simple(QuestionCategory category, List<String> ragKeywords) {
        return new QuestionProfile(category, false, null, List.of(), ragKeywords);
    }

    public boolean hasFramingOverride() {
        return framingNote != null && !framingNote.isBlank();
    }
}
