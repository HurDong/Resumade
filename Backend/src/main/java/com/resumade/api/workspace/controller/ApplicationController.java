package com.resumade.api.workspace.controller;

import com.resumade.api.workspace.domain.Application;
import com.resumade.api.workspace.domain.ApplicationRepository;
import com.resumade.api.workspace.domain.ApplicationResult;
import com.resumade.api.workspace.domain.ApplicationStatus;
import com.resumade.api.workspace.domain.WorkspaceQuestion;
import com.resumade.api.workspace.domain.WorkspaceQuestionRepository;
import com.resumade.api.workspace.dto.CompanyResearchRequest;
import com.resumade.api.workspace.dto.JdAnalysisResponse;
import com.resumade.api.workspace.service.CompanyResearchService;
import com.resumade.api.workspace.service.JdAnalysisService;
import com.resumade.api.workspace.service.WorkspaceService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationRepository applicationRepository;
    private final WorkspaceQuestionRepository questionRepository;
    private final ObjectMapper objectMapper;
    private final JdAnalysisService jdAnalysisService;
    private final CompanyResearchService companyResearchService;
    private final WorkspaceService workspaceService;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @GetMapping("/questions/{id}/rag")
    public ResponseEntity<com.resumade.api.workspace.dto.ExperienceContextResponse> getRagContext(
            @PathVariable Long id, 
            @RequestParam(required = false) String query) {
        List<com.resumade.api.workspace.dto.ExperienceContextResponse.ContextItem> context = workspaceService.getMatchedExperiences(id, query);
        return ResponseEntity.ok(new com.resumade.api.workspace.dto.ExperienceContextResponse(context));
    }

    @GetMapping("/analyze")
    public String testAnalyze() {
        log.info("Test GET request received at /api/applications/analyze");
        return "Backend JD Analysis endpoint is alive!";
    }

    @PostMapping("/analyze/init")
    public Map<String, String> initAnalyze(@RequestBody JdAnalysisRequest request) {
        String uuid = jdAnalysisService.initAnalysis(request.getRawJd());
        return Map.of("uuid", uuid);
    }

    @PostMapping("/analyze/upload")
    public Map<String, String> uploadAnalyze(@RequestParam("image") org.springframework.web.multipart.MultipartFile file) throws java.io.IOException {
        String uuid = jdAnalysisService.initImageAnalysis(file.getBytes());
        return Map.of("uuid", uuid);
    }

    @GetMapping("/analyze/stream/{uuid}")
    public SseEmitter streamAnalyze(@PathVariable String uuid) {
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(5).toMillis());
        executorService.execute(() -> jdAnalysisService.processAnalysis(uuid, emitter));
        return emitter;
    }

    @GetMapping("/analyze/image/stream/{uuid}")
    public SseEmitter streamImageAnalyze(@PathVariable String uuid) {
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(5).toMillis());
        executorService.execute(() -> jdAnalysisService.processImageAnalysis(uuid, emitter));
        return emitter;
    }

    @PostMapping("/{id}/company-research/init")
    public Map<String, String> initCompanyResearch(@PathVariable Long id,
                                                   @RequestBody(required = false) CompanyResearchRequest request) {
        String uuid = companyResearchService.initResearch(id, request);
        return Map.of("uuid", uuid);
    }

    @GetMapping("/company-research/stream/{uuid}")
    public SseEmitter streamCompanyResearch(@PathVariable String uuid) {
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(5).toMillis());
        executorService.execute(() -> companyResearchService.processResearch(uuid, emitter));
        return emitter;
    }

    @Transactional
    @PostMapping("/full")
    public Application createFullApplication(@RequestBody FullApplicationRequest request) {
        Application application = Application.builder()
                .companyName(request.getCompanyName())
                .position(request.getPosition())
                .rawJd(request.getRawJd())
                .aiInsight(request.getAiInsight())
                .companyResearch(request.getCompanyResearch())
                .logoUrl(request.getLogoUrl())
                .build();
        
        List<WorkspaceQuestion> questions = request.getQuestions().stream()
                .map(q -> WorkspaceQuestion.builder()
                        .title(q.getTitle())
                        .maxLength(q.getMaxLength() != null ? q.getMaxLength() : 1000)
                        .build())
                .collect(Collectors.toList());
        
        questions.forEach(application::addQuestion);
        
        return applicationRepository.save(application);
    }

    @Data
    public static class JdAnalysisRequest {
        private String rawJd;
    }

    @Data
    public static class FullApplicationRequest {
        private String companyName;
        private String position;
        private String rawJd;
        private String aiInsight;
        private String companyResearch;
        private String logoUrl;
        private List<QuestionEntry> questions;
    }

    @Data
    public static class QuestionEntry {
        private String title;
        private Integer maxLength;
    }

    @GetMapping
    public List<Application> getAllApplications() {
        return applicationRepository.findAll();
    }

    @GetMapping("/workspace-selector")
    public List<Application> getWorkspaceSelectorApplications() {
        return applicationRepository.findAll().stream()
                .filter(app -> app.getStatus() == ApplicationStatus.DOCUMENT)
                .filter(app -> {
                    if (app.getQuestions().isEmpty()) return true;
                    return app.getQuestions().stream().anyMatch(q -> !q.isCompleted());
                })
                .collect(Collectors.toList());
    }

    @Transactional
    @PutMapping("/{id}")
    public Application updateApplication(@PathVariable Long id, @RequestBody JsonNode updates) {
        Application application = applicationRepository.findById(id).orElseThrow();

        String companyName = getOptionalText(updates, "companyName", "company_name");
        if (companyName != null) application.setCompanyName(companyName);

        String position = getOptionalText(updates, "position");
        if (position != null) application.setPosition(position);

        String rawJd = getOptionalText(updates, "rawJd", "rawJD", "raw_jd");
        if (rawJd != null) application.setRawJd(rawJd);

        String aiInsight = getOptionalText(updates, "aiInsight", "ai_insight");
        if (aiInsight != null) application.setAiInsight(aiInsight);

        String status = getOptionalText(updates, "status");
        if (status != null) application.setStatus(ApplicationStatus.fromId(status));

        String result = getOptionalText(updates, "result");
        if (result != null) application.setResult(ApplicationResult.fromId(result));

        String logoUrl = getOptionalText(updates, "logoUrl", "logo_url");
        if (logoUrl != null) application.setLogoUrl(logoUrl);

        String companyResearch = getOptionalText(updates, "companyResearch", "company_research");
        if (companyResearch != null) application.setCompanyResearch(companyResearch);

        if (hasAnyField(updates, "deadline")) {
            String deadlineStr = getOptionalText(updates, "deadline");
            if (deadlineStr != null && !deadlineStr.isBlank()) {
                try {
                    // Handle ISO-8601 (e.g., 2026-03-16T14:29:00.000Z)
                    if (deadlineStr.endsWith("Z")) {
                        // Convert UTC to System Local (KST) to avoid 9-hour shift
                        application.setDeadline(java.time.OffsetDateTime.parse(deadlineStr)
                                .atZoneSameInstant(java.time.ZoneId.systemDefault())
                                .toLocalDateTime());
                    } else {
                        // Handle YYYY-MM-DDTHH:mm format from datetime-local input
                        if (deadlineStr.length() == 16) {
                            deadlineStr += ":00";
                        }
                        application.setDeadline(java.time.LocalDateTime.parse(deadlineStr));
                    }
                } catch (Exception e) {
                    log.error("Failed to parse deadline: {}", deadlineStr, e);
                    // Keep existing deadline on parse failure or set to null?
                    // application.setDeadline(null); 
                }
            } else {
                application.setDeadline(null);
            }
        }

        return applicationRepository.saveAndFlush(application);
    }

    private boolean hasAnyField(JsonNode updates, String... fieldNames) {
        for (String fieldName : fieldNames) {
            if (updates.has(fieldName)) {
                return true;
            }
        }
        return false;
    }

    private String getOptionalText(JsonNode updates, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode field = updates.get(fieldName);
            if (field == null || field.isMissingNode()) {
                continue;
            }
            if (field.isNull()) {
                return null;
            }
            return field.asText();
        }
        return null;
    }

    @Transactional
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteApplication(@PathVariable Long id) {
        Application application = applicationRepository.findById(id).orElseThrow();
        applicationRepository.delete(application);
        return ResponseEntity.ok().build();
    }

    @PostMapping
    public Application createApplication(@RequestBody Application application) {
        return applicationRepository.save(application);
    }

    @GetMapping("/{id}")
    public Application getApplication(@PathVariable Long id) {
        return applicationRepository.findById(id).orElseThrow();
    }

    @PostMapping("/{applicationId}/questions")
    public WorkspaceQuestion addQuestion(@PathVariable Long applicationId, @RequestBody WorkspaceQuestion question) {
        log.info("Adding question to application ID: {}", applicationId);
        Application app = applicationRepository.findById(applicationId).orElseThrow();
        app.addQuestion(question);
        WorkspaceQuestion saved = questionRepository.save(question);
        applicationRepository.save(app); // Ensure relation is persisted
        return saved;
    }

    @Transactional
    @PutMapping("/questions/{questionId}")
    public WorkspaceQuestion updateQuestion(@PathVariable Long questionId, @RequestBody WorkspaceQuestion questionDetails) {
        try {
            log.info("Updating question ID: {} with data: {}", questionId, questionDetails);
            WorkspaceQuestion question = questionRepository.findById(questionId)
                    .orElseThrow(() -> new RuntimeException("Question not found with id: " + questionId));
            
            if (questionDetails.getTitle() != null) question.setTitle(questionDetails.getTitle());
            if (questionDetails.getMaxLength() != null) question.setMaxLength(questionDetails.getMaxLength());
            if (questionDetails.getContent() != null) question.setContent(questionDetails.getContent());
            if (questionDetails.getWashedKr() != null) question.setWashedKr(questionDetails.getWashedKr());
            if (questionDetails.getMistranslations() != null) question.setMistranslations(questionDetails.getMistranslations());
            if (questionDetails.getAiReview() != null) question.setAiReview(questionDetails.getAiReview());
            if (questionDetails.getUserDirective() != null) question.setUserDirective(questionDetails.getUserDirective());
            question.setCompleted(questionDetails.isCompleted());
            
            WorkspaceQuestion saved = questionRepository.save(question);
            log.info("Successfully updated question ID: {}", questionId);
            return saved;
        } catch (Exception e) {
            log.error("Failed to update question ID: {}. Error: {}", questionId, e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    @DeleteMapping("/questions/{questionId}")
    public ResponseEntity<?> deleteQuestion(@PathVariable Long questionId) {
        WorkspaceQuestion question = questionRepository.findById(questionId).orElseThrow();
        Application app = question.getApplication();
        if (app != null) {
            app.getQuestions().remove(question);
        }
        questionRepository.delete(question);
        return ResponseEntity.ok().build();
    }
}
