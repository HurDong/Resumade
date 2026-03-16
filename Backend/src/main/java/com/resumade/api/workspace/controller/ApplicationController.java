package com.resumade.api.workspace.controller;

import com.resumade.api.workspace.domain.Application;
import com.resumade.api.workspace.domain.ApplicationRepository;
import com.resumade.api.workspace.domain.ApplicationResult;
import com.resumade.api.workspace.domain.ApplicationStatus;
import com.resumade.api.workspace.domain.WorkspaceQuestion;
import com.resumade.api.workspace.domain.WorkspaceQuestionRepository;
import com.resumade.api.workspace.dto.JdAnalysisResponse;
import com.resumade.api.workspace.service.JdAnalysisService;
import com.resumade.api.workspace.service.WorkspaceAiService;
import com.resumade.api.workspace.service.WorkspaceService;
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
    private final WorkspaceAiService aiService;
    private final ObjectMapper objectMapper;
    private final JdAnalysisService jdAnalysisService;
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

    @Transactional
    @PostMapping("/full")
    public Application createFullApplication(@RequestBody FullApplicationRequest request) {
        Application application = Application.builder()
                .companyName(request.getCompanyName())
                .position(request.getPosition())
                .rawJd(request.getRawJd())
                .aiInsight(request.getAiInsight())
                .logoUrl(request.getLogoUrl())
                .build();
        
        List<WorkspaceQuestion> questions = request.getQuestions().stream()
                .map(q -> WorkspaceQuestion.builder()
                        .title(q)
                        .maxLength(1000) // Default
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
        private String logoUrl;
        private List<String> questions;
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
    public Application updateApplication(@PathVariable Long id, @RequestBody Map<String, Object> updates) {
        Application application = applicationRepository.findById(id).orElseThrow();
        
        if (updates.containsKey("companyName")) application.setCompanyName((String) updates.get("companyName"));
        if (updates.containsKey("position")) application.setPosition((String) updates.get("position"));
        if (updates.containsKey("status")) application.setStatus(ApplicationStatus.fromId((String) updates.get("status")));
        if (updates.containsKey("result")) application.setResult(ApplicationResult.fromId((String) updates.get("result")));
        if (updates.containsKey("logoUrl")) application.setLogoUrl((String) updates.get("logoUrl"));
        if (updates.containsKey("deadline")) {
            String deadlineStr = (String) updates.get("deadline");
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
        
        return applicationRepository.save(application);
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
