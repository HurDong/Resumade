package com.resumade.api.workspace.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.infra.sse.Utf8SseSupport;
import com.resumade.api.workspace.domain.Application;
import com.resumade.api.workspace.domain.ApplicationRepository;
import com.resumade.api.workspace.dto.CompanyResearchRequest;
import com.resumade.api.workspace.dto.CompanyResearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyResearchService {

    private static final String FALLBACK = "미지정";

    private final GeminiCompanyResearchClient geminiCompanyResearchClient;
    private final ApplicationRepository applicationRepository;
    private final ObjectMapper objectMapper;
    private final Map<String, ResearchJob> researchCache = new ConcurrentHashMap<>();

    public String initResearch(Long applicationId, CompanyResearchRequest request) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found: " + applicationId));

        String additionalFocus = (request != null && request.getAdditionalFocus() != null)
                ? request.getAdditionalFocus().trim()
                : "";

        String uuid = UUID.randomUUID().toString();
        researchCache.put(uuid, new ResearchJob(applicationId, application, additionalFocus));
        log.info("Initialized company research for application {} with UUID {}", applicationId, uuid);
        return uuid;
    }

    @Transactional
    public void processResearch(String uuid, SseEmitter emitter) {
        ResearchJob job = researchCache.remove(uuid);
        if (job == null) {
            sendError(emitter, "유효하지 않거나 만료된 기업 분석 세션입니다.");
            return;
        }

        try {
            String company  = blank(job.application().getCompanyName()) ? FALLBACK : job.application().getCompanyName();
            String position = blank(job.application().getPosition())    ? FALLBACK : job.application().getPosition();
            String rawJd    = blank(job.application().getRawJd())       ? "JD 정보 없음" : job.application().getRawJd();

            sendEvent(emitter, "START",     "🔍 기업 및 직무 맥락을 파악하고 있어요.");
            sendEvent(emitter, "ANALYZING", "📋 경력 공고와 기술 블로그를 검색하고 있어요.");

            CompanyResearchResponse response = geminiCompanyResearchClient.compose(
                    company, position, rawJd, job.additionalFocus()
            );

            Application application = applicationRepository.findById(job.applicationId()).orElseThrow();
            application.setCompanyResearch(objectMapper.writeValueAsString(response));
            applicationRepository.save(application);

            sendEvent(emitter, "COMPLETE", response);
            emitter.complete();
            log.info("Completed company research for application {}", job.applicationId());
        } catch (Exception e) {
            log.error("Company research failed for UUID {}: {}", uuid, e.getMessage(), e);
            sendError(emitter, e.getMessage());
        }
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            if (data instanceof String text) {
                emitter.send(Utf8SseSupport.textEvent(name, text));
            } else {
                emitter.send(Utf8SseSupport.jsonEvent(name, data));
            }
        } catch (IOException | IllegalStateException e) {
            log.warn("Failed to send company research SSE event: {}", e.getMessage());
        }
    }

    private void sendError(SseEmitter emitter, String message) {
        try {
            emitter.send(Utf8SseSupport.jsonEvent("ERROR", Map.of("message", message)));
            emitter.complete();
        } catch (IOException | IllegalStateException e) {
            log.warn("Failed to send company research error event: {}", e.getMessage());
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record ResearchJob(Long applicationId, Application application, String additionalFocus) {}
}
