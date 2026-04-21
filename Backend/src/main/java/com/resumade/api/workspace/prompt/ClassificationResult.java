package com.resumade.api.workspace.prompt;

import java.util.List;

/**
 * 문항 분류 결과. 주 카테고리와 함께 복합 문항일 경우 모든 요구 항목(intent)을 담습니다.
 *
 * <p>단순 문항이면 allIntents는 비어 있고 isCompound는 false입니다.
 * 복합 문항이면 allIntents에 각 세부 ask가 담기며, PromptFactory가 이를
 * {@code <Additional_Requirements>} 블록으로 주입합니다.
 */
public record ClassificationResult(
        QuestionCategory primaryCategory,
        List<String> allIntents,
        boolean isCompound
) {
    public static ClassificationResult simple(QuestionCategory category) {
        return new ClassificationResult(category, List.of(), false);
    }

    public static ClassificationResult compound(QuestionCategory category, List<String> intents) {
        return new ClassificationResult(category, List.copyOf(intents), true);
    }
}
