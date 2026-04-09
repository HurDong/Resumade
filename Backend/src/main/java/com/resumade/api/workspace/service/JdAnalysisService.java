package com.resumade.api.workspace.service;

import com.resumade.api.infra.sse.Utf8SseSupport;
import com.resumade.api.workspace.dto.JdAnalysisResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class JdAnalysisService {

    private static final long HEARTBEAT_INTERVAL_SECONDS = 8L;

    private final JdTextAiService jdTextAiService;
    private final JdVisionGeminiService jdVisionGeminiService;
    private final Map<String, String> jdCache = new ConcurrentHashMap<>();
    private final Map<String, byte[]> imageCache = new ConcurrentHashMap<>();

    public String initAnalysis(String rawJd) {
        String uuid = UUID.randomUUID().toString();
        jdCache.put(uuid, rawJd);
        log.info("Initialized JD analysis for UUID: {}", uuid);
        return uuid;
    }

    public String initImageAnalysis(byte[] imageBytes) {
        String uuid = UUID.randomUUID().toString();
        imageCache.put(uuid, imageBytes);
        log.info("Initialized JD Image analysis for UUID: {}", uuid);
        return uuid;
    }

    public void processAnalysis(String uuid, SseEmitter emitter) {
        String rawJd = jdCache.remove(uuid);
        if (rawJd == null) {
            sendError(emitter, "세션이 만료되었습니다. 다시 시도해 주세요. ⚠️");
            return;
        }

        HeartbeatHandle heartbeat = startHeartbeat(emitter);
        try {
            sendEvent(emitter, "START", "채용 공고 분석을 시작합니다. 🔍");
            sendEvent(emitter, "ANALYZING", "공고의 핵심 직무 역량을 구조화하고 있어요. 🏷️");

            JdAnalysisResponse response = jdTextAiService.analyzeJd(rawJd);

            sendEvent(emitter, "COMPLETE", response);
            emitter.complete();
            log.info("Completed JD analysis for UUID: {}", uuid);
        } catch (Exception e) {
            log.error("JD Analysis failed for UUID: {}: {}", uuid, e.getMessage(), e);
            sendError(emitter, "AI 분석 중 오류가 발생했습니다: " + e.getMessage());
        } finally {
            heartbeat.stop();
        }
    }

    public void processImageAnalysis(String uuid, SseEmitter emitter) {
        byte[] imageBytes = imageCache.remove(uuid);
        if (imageBytes == null) {
            sendError(emitter, "세션이 만료되었습니다. 다시 시도해 주세요. ⚠️");
            return;
        }

        HeartbeatHandle heartbeat = startHeartbeat(emitter);
        try {
            sendEvent(emitter, "START", "공고 이미지 분석을 시작합니다. 🖼️");
            sendEvent(emitter, "ANALYZING", "AI가 이미지에서 공고 내용을 추출하고 있어요. 🤖");

            JdAnalysisResponse response = jdVisionGeminiService.analyzeFromImage(imageBytes, "image/png");

            sendEvent(emitter, "COMPLETE", response);
            emitter.complete();
            log.info("Completed Hybrid JD Image analysis for UUID: {}", uuid);
        } catch (Exception e) {
            log.error("JD Image Analysis failed for UUID: {}: {}", uuid, e.getMessage(), e);
            sendError(emitter, "이미지 분석 중 오류가 발생했습니다: " + e.getMessage());
        } finally {
            heartbeat.stop();
        }
    }

    private HeartbeatHandle startHeartbeat(SseEmitter emitter) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException | IllegalStateException e) {
                log.debug("Stopping JD analysis heartbeat: {}", e.getMessage());
                throw new RuntimeException(e);
            }
        }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);

        return new HeartbeatHandle(scheduler, future);
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            if (data instanceof String text) {
                emitter.send(Utf8SseSupport.textEvent(name, text));
            } else {
                emitter.send(Utf8SseSupport.jsonEvent(name, data));
            }
        } catch (IOException | IllegalStateException e) {
            log.warn("Failed to send SSE event: {}", e.getMessage());
        }
    }

    private void sendError(SseEmitter emitter, String message) {
        try {
            emitter.send(Utf8SseSupport.jsonEvent("ERROR", Map.of("message", message)));
            emitter.complete();
        } catch (IOException | IllegalStateException e) {
            log.warn("Failed to send error event: {}", e.getMessage());
        }
    }

    private record HeartbeatHandle(ScheduledExecutorService scheduler, ScheduledFuture<?> future) {
        private void stop() {
            future.cancel(true);
            scheduler.shutdownNow();
        }
    }
}
