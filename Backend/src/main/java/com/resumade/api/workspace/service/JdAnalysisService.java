package com.resumade.api.workspace.service;

import com.resumade.api.workspace.dto.JdAnalysisResponse;
import com.resumade.api.workspace.service.WorkspaceAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class JdAnalysisService {

    private final WorkspaceAiService aiService;
    private final Map<String, String> jdCache = new ConcurrentHashMap<>();

    public String initAnalysis(String rawJd) {
        String uuid = UUID.randomUUID().toString();
        jdCache.put(uuid, rawJd);
        log.info("Initialized JD analysis for UUID: {}", uuid);
        return uuid;
    }

    public void processAnalysis(String uuid, SseEmitter emitter) {
        String rawJd = jdCache.remove(uuid);
        if (rawJd == null) {
            sendError(emitter, "Invalid or expired session");
            return;
        }

        try {
            sendEvent(emitter, "START", "분석을 시작합니다...");
            
            // Artificial delay to show progress UI if it's too fast (though GPT is usually slow)
            sendEvent(emitter, "ANALYZING", "공고 내용을 추출 중입니다...");
            
            JdAnalysisResponse response = aiService.analyzeJd(rawJd);
            
            sendEvent(emitter, "COMPLETE", response);
            emitter.complete();
            log.info("Completed JD analysis for UUID: {}", uuid);
        } catch (Exception e) {
            log.error("JD Analysis failed for UUID: {}: {}", uuid, e.getMessage(), e);
            sendError(emitter, "AI 분석 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            emitter.send(SseEmitter.event().name(name).data(data));
        } catch (IOException e) {
            log.warn("Failed to send SSE event: {}", e.getMessage());
        }
    }

    private void sendError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event().name("ERROR").data(Map.of("message", message)));
            emitter.complete();
        } catch (IOException e) {
            log.warn("Failed to send error event: {}", e.getMessage());
        }
    }
}
