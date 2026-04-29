package com.resumade.api.workspace.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.infra.sse.Utf8SseSupport;
import com.resumade.api.workspace.domain.Application;
import com.resumade.api.workspace.domain.ApplicationRepository;
import com.resumade.api.workspace.domain.CompanyFitProfile;
import com.resumade.api.workspace.domain.CompanyFitProfileRepository;
import com.resumade.api.workspace.dto.CompanyFitProfileActivateRequest;
import com.resumade.api.workspace.dto.CompanyFitProfileCandidateResponse;
import com.resumade.api.workspace.dto.CompanyFitProfileDto;
import com.resumade.api.workspace.dto.CompanyFitProfileRequest;
import com.resumade.api.workspace.dto.CompanyFitProfileResponse;
import com.resumade.api.workspace.dto.CompanyFitProfileReviewNoteRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyFitProfileService {

    private static final Duration CANDIDATE_TTL = Duration.ofMinutes(30);
    private static final String DEFAULT_RAW_JD = "No JD provided.";

    private final ApplicationRepository applicationRepository;
    private final CompanyFitProfileRepository companyFitProfileRepository;
    private final GeminiCompanyFitProfileClient geminiCompanyFitProfileClient;
    private final ObjectMapper objectMapper;
    private final Map<String, FitProfileJob> jobCache = new ConcurrentHashMap<>();
    private final Map<String, FitProfileCandidate> candidateCache = new ConcurrentHashMap<>();

    public String initProfile(Long applicationId, CompanyFitProfileRequest request) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));

        String additionalFocus = request == null || request.getAdditionalFocus() == null
                ? ""
                : request.getAdditionalFocus().trim();

        String uuid = UUID.randomUUID().toString();
        jobCache.put(uuid, new FitProfileJob(applicationId, application, additionalFocus));
        cleanupExpiredCandidates();
        log.info("Initialized company fit profile for application {} with UUID {}", applicationId, uuid);
        return uuid;
    }

    public void processProfile(String uuid, SseEmitter emitter) {
        FitProfileJob job = jobCache.remove(uuid);
        if (job == null) {
            sendError(emitter, "Fit profile session expired. Please generate it again.");
            return;
        }

        try {
            sendEvent(emitter, "START", "기업 Fit 프로필 생성을 시작합니다.");
            sendEvent(emitter, "SEARCHING", "Gemini가 공고와 웹 근거를 확인하고 있습니다.");

            Application application = job.application();
            GeminiCompanyFitProfileClient.GenerationResult result = geminiCompanyFitProfileClient.generate(
                    application.getCompanyName(),
                    application.getPosition(),
                    blank(application.getRawJd()) ? DEFAULT_RAW_JD : application.getRawJd(),
                    application.getAiInsight(),
                    job.additionalFocus()
            );

            sendEvent(emitter, "STRUCTURING", "지원 전략 가설을 프로필 후보로 정리하고 있습니다.");

            Instant expiresAt = Instant.now().plus(CANDIDATE_TTL);
            FitProfileCandidate candidate = new FitProfileCandidate(
                    uuid,
                    job.applicationId(),
                    result.profile(),
                    result.modelName(),
                    result.groundingStatus(),
                    expiresAt
            );
            candidateCache.put(uuid, candidate);

            sendEvent(emitter, "COMPLETE", toCandidateResponse(candidate));
            emitter.complete();
        } catch (Exception e) {
            log.error("Company fit profile generation failed for UUID {}: {}", uuid, e.getMessage(), e);
            sendError(emitter, e.getMessage());
        }
    }

    @Transactional
    public CompanyFitProfileResponse activateProfile(Long applicationId, CompanyFitProfileActivateRequest request) {
        if (request == null || request.uuid() == null || request.uuid().isBlank()) {
            throw new IllegalArgumentException("uuid is required.");
        }

        cleanupExpiredCandidates();
        FitProfileCandidate candidate = candidateCache.get(request.uuid());
        if (candidate == null) {
            throw new IllegalStateException("Fit profile candidate expired. Please generate it again.");
        }
        if (!candidate.applicationId().equals(applicationId)) {
            throw new IllegalArgumentException("Fit profile candidate does not belong to this application.");
        }

        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + applicationId));

        CompanyFitProfile entity = companyFitProfileRepository.findByApplicationId(applicationId)
                .orElseGet(() -> new CompanyFitProfile(application));

        entity.setProfileJson(writeProfileJson(candidate.profile()));
        entity.setReviewNote(null);
        entity.setModelName(candidate.modelName());
        entity.setGroundingStatus(candidate.groundingStatus());

        CompanyFitProfile saved = companyFitProfileRepository.save(entity);
        candidateCache.remove(request.uuid());
        return toResponse(saved);
    }

    public void discardCandidate(Long applicationId, String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return;
        }
        FitProfileCandidate candidate = candidateCache.get(uuid);
        if (candidate != null && candidate.applicationId().equals(applicationId)) {
            candidateCache.remove(uuid);
        }
    }

    @Transactional(readOnly = true)
    public Optional<CompanyFitProfileResponse> getActiveProfile(Long applicationId) {
        return companyFitProfileRepository.findByApplicationId(applicationId)
                .map(this::toResponse);
    }

    @Transactional
    public CompanyFitProfileResponse updateReviewNote(Long applicationId, CompanyFitProfileReviewNoteRequest request) {
        CompanyFitProfile entity = companyFitProfileRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> new IllegalStateException("No active fit profile exists for this application."));

        entity.setReviewNote(request == null ? null : request.reviewNote());
        return toResponse(entity);
    }

    public ResponseEntity<CompanyFitProfileResponse> responseOrNoContent(Long applicationId) {
        return getActiveProfile(applicationId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    private CompanyFitProfileCandidateResponse toCandidateResponse(FitProfileCandidate candidate) {
        return CompanyFitProfileCandidateResponse.builder()
                .uuid(candidate.uuid())
                .applicationId(candidate.applicationId())
                .profile(candidate.profile())
                .modelName(candidate.modelName())
                .groundingStatus(candidate.groundingStatus())
                .expiresAt(candidate.expiresAt())
                .build();
    }

    private CompanyFitProfileResponse toResponse(CompanyFitProfile entity) {
        return CompanyFitProfileResponse.builder()
                .id(entity.getId())
                .applicationId(entity.getApplication().getId())
                .profile(readProfileJson(entity.getProfileJson()))
                .reviewNote(entity.getReviewNote())
                .modelName(entity.getModelName())
                .groundingStatus(entity.getGroundingStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private String writeProfileJson(CompanyFitProfileDto profile) {
        try {
            return objectMapper.writeValueAsString(profile);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize fit profile.", e);
        }
    }

    private CompanyFitProfileDto readProfileJson(String json) {
        try {
            return objectMapper.readValue(json, CompanyFitProfileDto.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse stored fit profile.", e);
        }
    }

    private void cleanupExpiredCandidates() {
        Instant now = Instant.now();
        candidateCache.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private void sendEvent(SseEmitter emitter, String name, Object data) {
        try {
            if (data instanceof String text) {
                emitter.send(Utf8SseSupport.textEvent(name, text));
            } else {
                emitter.send(Utf8SseSupport.jsonEvent(name, data));
            }
        } catch (IOException | IllegalStateException e) {
            log.warn("Failed to send company fit profile SSE event: {}", e.getMessage());
        }
    }

    private void sendError(SseEmitter emitter, String message) {
        try {
            emitter.send(Utf8SseSupport.jsonEvent("ERROR", Map.of("message", message)));
            emitter.complete();
        } catch (IOException | IllegalStateException e) {
            log.warn("Failed to send company fit profile error event: {}", e.getMessage());
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record FitProfileJob(Long applicationId, Application application, String additionalFocus) {
    }

    private record FitProfileCandidate(
            String uuid,
            Long applicationId,
            CompanyFitProfileDto profile,
            String modelName,
            String groundingStatus,
            Instant expiresAt
    ) {
    }
}
