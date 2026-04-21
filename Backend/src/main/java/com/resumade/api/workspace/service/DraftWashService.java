package com.resumade.api.workspace.service;

import com.resumade.api.infra.sse.Utf8SseSupport;
import com.resumade.api.workspace.dto.DraftWashRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DraftWashService {

    private static final int MAX_DRAFT_LENGTH = 12_000;

    private final TranslationService translationService;

    public void process(DraftWashRequest request, SseEmitter emitter) {
        try {
            String draftText = request != null ? request.draftText() : null;
            if (draftText == null || draftText.isBlank()) {
                sendError(emitter, "세탁할 초안을 입력해 주세요.");
                return;
            }

            String normalizedDraft = normalize(draftText);
            if (normalizedDraft.length() > MAX_DRAFT_LENGTH) {
                sendError(emitter, "초안은 12,000자 이하로 입력해 주세요.");
                return;
            }

            sendProgress(emitter, "received", "초안을 접수했습니다.");
            sendProgress(emitter, "toEnglish", "영어 번역을 진행하고 있습니다.");
            String englishText = translationService.translateToEnglish(normalizedDraft);
            String safeEnglishText = englishText != null ? englishText : normalizedDraft;

            sendProgress(emitter, "toKorean", "한국어 재번역을 진행하고 있습니다.");
            String washedText = translationService.translateToKorean(safeEnglishText);
            String safeWashedText = washedText != null ? washedText : safeEnglishText;

            sendProgress(emitter, "ready", "WASH본을 준비했습니다.");
            sendEvent(emitter, "COMPLETE", Map.of(
                    "originalText", normalizedDraft,
                    "englishText", safeEnglishText,
                    "washedText", safeWashedText,
                    "originalChars", normalizedDraft.length(),
                    "washedChars", safeWashedText.length()
            ));
        } catch (Exception e) {
            log.error("Draft wash failed", e);
            sendError(emitter, "세탁 중 오류가 발생했습니다. 번역 API 설정을 확인해 주세요.");
        } finally {
            emitter.complete();
        }
    }

    private String normalize(String text) {
        return text.replace("\r\n", "\n").replace("\r", "\n").strip();
    }

    private void sendProgress(SseEmitter emitter, String stage, String message) throws IOException {
        sendEvent(emitter, "PROGRESS", Map.of("stage", stage, "message", message));
    }

    private void sendError(SseEmitter emitter, String message) {
        try {
            sendEvent(emitter, "ERROR", Map.of("message", message));
        } catch (IOException e) {
            log.warn("Failed to send draft wash error event", e);
        }
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) throws IOException {
        emitter.send(Utf8SseSupport.jsonEvent(name, data));
    }
}
