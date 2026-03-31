package com.resumade.api.workspace.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.data.message.AiMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * {@link com.resumade.api.workspace.prompt.PromptFactory}가 조립한
 * {@code List<ChatMessage>}를 직접 {@link ChatLanguageModel}로 실행하는 서비스.
 *
 * <h2>기존 구조와의 차이</h2>
 * <table border="1">
 *   <tr><th>기존</th><th>신규</th></tr>
 *   <tr>
 *     <td>WorkspaceDraftAiService (LangChain4j AiServices 래퍼) — 하드코딩 프롬프트</td>
 *     <td>StrategyDraftGeneratorService — PromptFactory가 조립한 메시지 직접 실행</td>
 *   </tr>
 * </table>
 *
 * <h2>주입받는 모델</h2>
 * AiConfig에서 {@code @Bean(name = "workspaceDraftChatModel")}으로 등록된
 * {@link ChatLanguageModel}을 사용합니다.
 * 기존 {@code workspaceDraftAiService}와 동일한 모델(gpt-5-mini 등)을 공유합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyDraftGeneratorService {

    private final ChatLanguageModel workspaceDraftChatModel;
    private final ObjectMapper objectMapper;

    /**
     * PromptFactory가 조립한 메시지 리스트로 초안을 생성합니다.
     *
     * @param messages PromptFactory.buildMessages()의 반환값
     * @return 파싱된 DraftResponse
     * @throws RuntimeException JSON 파싱 실패 또는 모델 호출 오류 시
     */
    public WorkspaceDraftAiService.DraftResponse generate(List<ChatMessage> messages) {
        try {
            Response<AiMessage> response = workspaceDraftChatModel.generate(messages);
            String rawJson = response.content().text();

            log.debug("StrategyDraftGeneratorService: raw response length={}", rawJson != null ? rawJson.length() : 0);

            return objectMapper.readValue(sanitizeJson(rawJson), WorkspaceDraftAiService.DraftResponse.class);

        } catch (Exception e) {
            log.error("StrategyDraftGeneratorService: generation failed. messageCount={}", messages.size(), e);
            throw new RuntimeException("Strategy-based draft generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * LLM 응답에서 마크다운 코드 펜스(```json ... ```)를 제거합니다.
     * JSON 모드가 강제된 경우에도 일부 모델이 펜스를 포함할 수 있습니다.
     */
    private static String sanitizeJson(String raw) {
        if (raw == null) return "{}";
        String trimmed = raw.strip();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            trimmed = trimmed.replaceAll("```\\s*$", "").strip();
        }
        return trimmed;
    }
}
