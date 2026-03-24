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

    private static final String DEFAULT_VALUE = "\ubbf8\uc9c0\uc815";
    private static final String DEFAULT_GOAL = "\uc9c0\uc6d0\ub3d9\uae30, \uc11c\ube44\uc2a4 \ud638\uac10\ub3c4, \uc9c1\ubb34 \uc801\ud569\ub3c4\ub97c \uc790\uc18c\uc11c\uc5d0 \uad6c\uccb4\uc801\uc73c\ub85c \ub179\uc77c \uc218 \uc788\uc744 \uc815\ub3c4\ub85c \ubd84\uc11d";
    private static final String DEFAULT_JD = "JD \uc815\ubcf4 \uc5c6\uc74c";

    private final GeminiCompanyResearchClient geminiCompanyResearchClient;
    private final ApplicationRepository applicationRepository;
    private final ObjectMapper objectMapper;
    private final Map<String, ResearchJob> researchCache = new ConcurrentHashMap<>();

    public String initResearch(Long applicationId, CompanyResearchRequest request) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found: " + applicationId));

        CompanyResearchRequest normalizedRequest = request != null ? request : new CompanyResearchRequest();
        String uuid = UUID.randomUUID().toString();
        researchCache.put(uuid, new ResearchJob(applicationId, application, normalizedRequest));
        log.info("Initialized company research for application {} with UUID {}", applicationId, uuid);
        return uuid;
    }

    @Transactional
    public void processResearch(String uuid, SseEmitter emitter) {
        ResearchJob job = researchCache.remove(uuid);
        if (job == null) {
            sendError(emitter, "\uc720\ud6a8\ud558\uc9c0 \uc54a\uac70\ub098 \ub9cc\ub8cc\ub41c \uae30\uc5c5 \ubd84\uc11d \uc138\uc158\uc785\ub2c8\ub2e4.");
            return;
        }

        try {
            String company = valueOrFallback(job.application().getCompanyName(), DEFAULT_VALUE);
            String position = valueOrFallback(job.application().getPosition(), DEFAULT_VALUE);
            String businessUnit = valueOrFallback(job.request().getBusinessUnit(), DEFAULT_VALUE);
            String targetService = valueOrFallback(job.request().getTargetService(), DEFAULT_VALUE);
            String focusRole = valueOrFallback(job.request().getFocusRole(), position);
            String techFocus = valueOrFallback(job.request().getTechFocus(), DEFAULT_VALUE);
            String questionGoal = valueOrFallback(job.request().getQuestionGoal(), DEFAULT_GOAL);
            String rawJd = valueOrFallback(job.application().getRawJd(), DEFAULT_JD);

            sendEvent(emitter, "START", "\ud83d\udd0d \uae30\uc5c5\uacfc \uc9c1\ubb34 \ub9e5\ub77d\uc744 \uc815\ub9ac\ud560 \uc900\ube44\ub97c \ud558\uace0 \uc788\uc5b4\uc694.");
            sendEvent(emitter, "ANALYZING", "\ud83e\udde0 \ud68c\uc0ac, \uc11c\ube44\uc2a4, \uc9c1\ubb34 \uc815\ubcf4\ub97c \uc790\uc18c\uc11c\uc5d0 \ub179\uc77c \uc218 \uc788\ub3c4\ub85d \uc2ec\uce35 \ubd84\uc11d\ud558\uace0 \uc788\uc5b4\uc694.");

            CompanyResearchResponse response = geminiCompanyResearchClient.compose(
                    company,
                    position,
                    businessUnit,
                    targetService,
                    focusRole,
                    techFocus,
                    questionGoal,
                    rawJd
            );
            response = applyFocusOverrides(response, job.request(), company, position);

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

    private String valueOrFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private CompanyResearchResponse applyFocusOverrides(
            CompanyResearchResponse response,
            CompanyResearchRequest request,
            String company,
            String position
    ) {
        CompanyResearchResponse safeResponse = response != null ? response : new CompanyResearchResponse();
        CompanyResearchResponse.Focus focus = safeResponse.getFocus() != null
                ? safeResponse.getFocus()
                : new CompanyResearchResponse.Focus();

        focus.setCompany(company);
        focus.setPosition(position);
        focus.setBusinessUnit(preferRequestValue(request.getBusinessUnit(), focus.getBusinessUnit()));
        focus.setTargetService(preferRequestValue(request.getTargetService(), focus.getTargetService()));
        focus.setFocusRole(preferRequestValue(request.getFocusRole(), valueOrFallback(focus.getFocusRole(), position)));
        focus.setTechFocus(preferRequestValue(request.getTechFocus(), focus.getTechFocus()));
        focus.setQuestionGoal(preferRequestValue(request.getQuestionGoal(), focus.getQuestionGoal()));

        safeResponse.setFocus(focus);
        return safeResponse;
    }

    private String preferRequestValue(String requestValue, String fallbackValue) {
        return requestValue != null && !requestValue.isBlank()
                ? requestValue.trim()
                : valueOrFallback(fallbackValue, "");
    }

    private record ResearchJob(Long applicationId, Application application, CompanyResearchRequest request) {
    }
}
