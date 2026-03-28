package com.resumade.api.workspace.prompt;

/**
 * Few-shot 예시 쌍.
 *
 * <p>LLM에게 제공되는 사용자 메시지 + 어시스턴트 응답 쌍으로,
 * PromptFactory가 {@code List<ChatMessage>}를 조립할 때 사용합니다.
 *
 * @param userMessage       사용자 메시지 (예시 문항 및 조건)
 * @param assistantMessage  어시스턴트 응답 (합격 자소서 수준의 예시 답변)
 */
public record FewShotExample(
        String userMessage,
        String assistantMessage
) {
    public FewShotExample {
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("FewShotExample userMessage must not be blank");
        }
        if (assistantMessage == null || assistantMessage.isBlank()) {
            throw new IllegalArgumentException("FewShotExample assistantMessage must not be blank");
        }
    }
}
