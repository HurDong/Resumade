package com.resumade.api.workspace.controller;

import com.resumade.api.workspace.domain.Application;
import com.resumade.api.workspace.domain.ApplicationRepository;
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
    public ResponseEntity<com.resumade.api.workspace.dto.ExperienceContextResponse> getRagContext(@PathVariable Long id) {
        List<com.resumade.api.workspace.dto.ExperienceContextResponse.ContextItem> context = workspaceService.getMatchedExperiences(id);
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

    @GetMapping("/analyze/stream/{uuid}")
    public SseEmitter streamAnalyze(@PathVariable String uuid) {
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(5).toMillis());
        executorService.execute(() -> jdAnalysisService.processAnalysis(uuid, emitter));
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
        private List<String> questions;
    }

    @GetMapping
    public List<Application> getAllApplications() {
        return applicationRepository.findAll();
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
