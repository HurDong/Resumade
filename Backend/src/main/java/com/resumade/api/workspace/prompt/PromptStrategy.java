package com.resumade.api.workspace.prompt;

import java.util.List;

/**
 * 문항 카테고리별 프롬프트 생성 전략 인터페이스.
 *
 * <h2>설계 원칙</h2>
 * <ul>
 *   <li>{@link #buildSystemPrompt()} — 기존 v1 파이프라인용. 카테고리 특화 시스템 프롬프트 전체.</li>
 *   <li>{@link #buildSystemPromptWithProfile(QuestionProfile)} — v2 파이프라인용. Layer1(Quality Rules)
 *       + Layer2(Structure Guide)로 분리되며, 복합 문항일 경우 Layer2를 QuestionProfile.framingNote로 오버라이드합니다.</li>
 *   <li>{@link #getFewShotExamples()} — 합격 자소서 수준의 입출력 예시 쌍.</li>
 *   <li>{@link #buildUserMessage(DraftParams)} — 실제 생성 요청 메시지.</li>
 * </ul>
 */
public interface PromptStrategy {

    /**
     * 이 전략이 담당하는 문항 카테고리.
     */
    QuestionCategory getCategory();

    /**
     * [v1] 카테고리 특화 시스템 프롬프트 (기존 파이프라인 호환용).
     */
    String buildSystemPrompt();

    /**
     * [v2] QuestionProfile을 반영한 동적 시스템 프롬프트.
     *
     * <p>기본 구현: buildSystemPrompt()를 기반으로, 복합/특수 문항이면
     * {@code <Question_Strategy_Override>} 블록을 {@code <Output_Format>} 직전에 주입합니다.
     * 이 블록은 LLM에게 기존 Draft_Structure 대신 framingNote를 따르도록 지시합니다.
     *
     * <p>각 전략 구현체는 이 메서드를 override하여 Layer1/Layer2 완전 분리를 제공할 수 있습니다.
     */
    default String buildSystemPromptWithProfile(QuestionProfile profile) {
        String base = buildSystemPrompt();
        if (profile == null || !profile.hasFramingOverride()) {
            return base;
        }
        String overrideBlock = buildStructureOverrideBlock(profile);
        int idx = base.lastIndexOf("<Output_Format>");
        return idx >= 0
                ? base.substring(0, idx) + overrideBlock + "\n" + base.substring(idx)
                : base + "\n\n" + overrideBlock;
    }

    private static String buildStructureOverrideBlock(QuestionProfile profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("<Question_Strategy_Override>\n");
        sb.append("IMPORTANT: The following strategy OVERRIDES the Draft_Structure above for this specific question.\n\n");
        sb.append(profile.framingNote()).append("\n");
        if (!profile.requiredElements().isEmpty()) {
            sb.append("\nRequired elements (also listed in Required_Elements block):\n");
            for (String el : profile.requiredElements()) {
                sb.append("- ").append(el).append("\n");
            }
            sb.append("→ Weave ALL elements into ONE unified narrative. No section headers or itemized paragraphs.\n");
        }
        sb.append("</Question_Strategy_Override>\n");
        return sb.toString();
    }

    /**
     * Few-shot 예시 쌍 목록.
     * 빈 리스트 반환 허용 (DEFAULT 전략 등에서 사용).
     */
    List<FewShotExample> getFewShotExamples();

    /**
     * 실제 생성 요청에 사용되는 유저 메시지 조립.
     *
     * @param params 초안 생성에 필요한 모든 파라미터
     * @return 완성된 유저 메시지 문자열
     */
    String buildUserMessage(DraftParams params);
}
