package com.resumade.api.workspace.controller;

import com.resumade.api.infra.sse.Utf8SseSupport;
import com.resumade.api.workspace.domain.Application;
import com.resumade.api.workspace.domain.ApplicationRepository;
import com.resumade.api.workspace.domain.ApplicationResult;
import com.resumade.api.workspace.domain.ApplicationStatus;
import com.resumade.api.workspace.domain.WorkspaceQuestion;
import com.resumade.api.workspace.domain.WorkspaceQuestionRepository;
import com.resumade.api.workspace.dto.CompanyFitProfileActivateRequest;
import com.resumade.api.workspace.dto.CompanyFitProfileRequest;
import com.resumade.api.workspace.dto.CompanyFitProfileResponse;
import com.resumade.api.workspace.dto.CompanyFitProfileReviewNoteRequest;
import com.resumade.api.workspace.dto.CompanyResearchRequest;
import com.resumade.api.workspace.dto.JdAnalysisResponse;
import com.resumade.api.workspace.service.CompanyFitProfileService;
import com.resumade.api.workspace.service.CompanyResearchService;
import com.resumade.api.workspace.service.JdAnalysisService;
import com.resumade.api.workspace.service.PdfTextExtractorService;
import com.resumade.api.workspace.service.QuestionImageParserService;
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
import java.time.LocalDateTime;
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
    private final PdfTextExtractorService pdfTextExtractorService;
    private final CompanyResearchService companyResearchService;
    private final CompanyFitProfileService companyFitProfileService;
    private final WorkspaceService workspaceService;
    private final QuestionImageParserService questionImageParserService;
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

    @PostMapping("/analyze/pdf/upload")
    public Map<String, String> uploadPdfAnalyze(@RequestParam("pdf") org.springframework.web.multipart.MultipartFile file) throws java.io.IOException {
        String extractedText = pdfTextExtractorService.extractText(file.getBytes());
        String uuid = jdAnalysisService.initAnalysis(extractedText);
        return Map.of("uuid", uuid);
    }

    @GetMapping(value = "/analyze/stream/{uuid}", produces = Utf8SseSupport.TEXT_EVENT_STREAM_UTF8_VALUE)
    public SseEmitter streamAnalyze(@PathVariable String uuid) {
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(5).toMillis());
        executorService.execute(() -> jdAnalysisService.processAnalysis(uuid, emitter));
        return emitter;
    }

    @GetMapping(value = "/analyze/image/stream/{uuid}", produces = Utf8SseSupport.TEXT_EVENT_STREAM_UTF8_VALUE)
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

    @GetMapping(value = "/company-research/stream/{uuid}", produces = Utf8SseSupport.TEXT_EVENT_STREAM_UTF8_VALUE)
    public SseEmitter streamCompanyResearch(@PathVariable String uuid) {
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(5).toMillis());
        executorService.execute(() -> companyResearchService.processResearch(uuid, emitter));
        return emitter;
    }

    @PostMapping("/{id}/fit-profile/init")
    public Map<String, String> initFitProfile(@PathVariable Long id,
                                              @RequestBody(required = false) CompanyFitProfileRequest request) {
        String uuid = companyFitProfileService.initProfile(id, request);
        return Map.of("uuid", uuid);
    }

    @GetMapping(value = "/fit-profile/stream/{uuid}", produces = Utf8SseSupport.TEXT_EVENT_STREAM_UTF8_VALUE)
    public SseEmitter streamFitProfile(@PathVariable String uuid) {
        SseEmitter emitter = new SseEmitter(Duration.ofMinutes(5).toMillis());
        executorService.execute(() -> companyFitProfileService.processProfile(uuid, emitter));
        return emitter;
    }

    @PostMapping("/{id}/fit-profile/activate")
    public CompanyFitProfileResponse activateFitProfile(@PathVariable Long id,
                                                        @RequestBody CompanyFitProfileActivateRequest request) {
        return companyFitProfileService.activateProfile(id, request);
    }

    @DeleteMapping("/{id}/fit-profile/candidate/{uuid}")
    public ResponseEntity<Void> discardFitProfileCandidate(@PathVariable Long id, @PathVariable String uuid) {
        companyFitProfileService.discardCandidate(id, uuid);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/fit-profile")
    public ResponseEntity<CompanyFitProfileResponse> getFitProfile(@PathVariable Long id) {
        return companyFitProfileService.responseOrNoContent(id);
    }

    @PatchMapping("/{id}/fit-profile/review-note")
    public CompanyFitProfileResponse updateFitProfileReviewNote(
            @PathVariable Long id,
            @RequestBody(required = false) CompanyFitProfileReviewNoteRequest request) {
        return companyFitProfileService.updateReviewNote(id, request);
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
    public static class ImportJasoseolRequest {
        @com.fasterxml.jackson.annotation.JsonProperty("id")
        private Long jasoseolId;
        private String name;
        private String title;
        @com.fasterxml.jackson.annotation.JsonProperty("end_time")
        private String endTime;
        @com.fasterxml.jackson.annotation.JsonProperty("image_file_name")
        private String imageFileName;
        private List<QuestionEntry> questions;
    }

    @Transactional
    @PostMapping("/import-jasoseol")
    public Application importFromJasoseol(@RequestBody ImportJasoseolRequest request) {
        LocalDateTime deadline = null;
        if (request.getEndTime() != null && !request.getEndTime().isBlank()) {
            try {
                deadline = java.time.OffsetDateTime.parse(request.getEndTime())
                        .atZoneSameInstant(java.time.ZoneId.systemDefault())
                        .toLocalDateTime();
            } catch (Exception e) {
                log.error("Failed to parse deadline for import: {}", request.getEndTime(), e);
            }
        }

        Application application = Application.builder()
                .companyName(request.getName() != null ? request.getName() : "이름 없음")
                .position(request.getTitle() != null ? request.getTitle() : "포지션명 없음")
                .logoUrl(request.getImageFileName())
                .deadline(deadline)
                .status(ApplicationStatus.DOCUMENT)
                .build();

        if (request.getQuestions() != null) {
            request.getQuestions().stream()
                    .filter(q -> q.getTitle() != null && !q.getTitle().isBlank())
                    .forEach(q -> {
                        WorkspaceQuestion wq = WorkspaceQuestion.builder()
                                .title(q.getTitle())
                                .maxLength(q.getMaxLength() != null ? q.getMaxLength() : 1000)
                                .build();
                        application.addQuestion(wq);
                    });
        }

        return applicationRepository.save(application);
    }

    @Data
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuestionEntry {
        @com.fasterxml.jackson.annotation.JsonAlias({"question", "title"})
        private String title;
        @com.fasterxml.jackson.annotation.JsonAlias({"word_limit", "maxLength", "total_count"})
        private Integer maxLength;
    }

    @GetMapping
    public List<Application> getAllApplications() {
        return applicationRepository.findAllWithQuestions();
    }

    @GetMapping("/workspace-selector")
    public List<Application> getWorkspaceSelectorApplications() {
        return applicationRepository.findAllWithQuestions().stream()
                .filter(this::isWorkspaceSelectable)
                .collect(Collectors.toList());
    }

    private boolean isWorkspaceSelectable(Application app) {
        return app.getResult() == ApplicationResult.PENDING;
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

        Application saved = applicationRepository.saveAndFlush(application);
        return applicationRepository.findByIdWithQuestions(saved.getId()).orElse(saved);
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
        return applicationRepository.findByIdWithQuestions(id).orElseThrow();
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
            if (questionDetails.getBatchStrategyDirective() != null) {
                question.setBatchStrategyDirective(questionDetails.getBatchStrategyDirective());
            }
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

    // ── 스크린샷 → 문항 파싱 (저장하지 않고 미리보기용 결과만 반환) ──────────────────
    @PostMapping("/{applicationId}/questions/parse-image")
    public List<QuestionImageParserService.ParsedQuestion> parseQuestionsFromImage(
            @PathVariable Long applicationId,
            @RequestParam("image") org.springframework.web.multipart.MultipartFile file
    ) throws java.io.IOException, InterruptedException {
        // applicationId 존재 검증
        applicationRepository.findById(applicationId).orElseThrow();
        String mimeType = file.getContentType() != null ? file.getContentType() : "image/png";
        log.info("Parsing questions from image: applicationId={}, size={}, type={}", applicationId, file.getSize(), mimeType);
        return questionImageParserService.parseFromImage(file.getBytes(), mimeType);
    }

    // ── 파싱된 문항 일괄 저장 ────────────────────────────────────────────────────────
    @Transactional
    @PostMapping("/{applicationId}/questions/bulk")
    public List<Map<String, Object>> bulkAddQuestions(
            @PathVariable Long applicationId,
            @RequestBody List<QuestionEntry> questions
    ) {
        Application app = applicationRepository.findById(applicationId).orElseThrow();
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        questions.stream()
                .filter(q -> q.getTitle() != null && !q.getTitle().isBlank())
                .forEach(q -> {
                    WorkspaceQuestion wq = WorkspaceQuestion.builder()
                            .title(q.getTitle().strip())
                            .maxLength(q.getMaxLength() != null ? q.getMaxLength() : 1000)
                            .build();
                    app.addQuestion(wq);
                    questionRepository.save(wq);
                    result.add(Map.of(
                            "id", wq.getId(),
                            "title", wq.getTitle(),
                            "maxLength", wq.getMaxLength()
                    ));
                });
        applicationRepository.save(app);
        log.info("Bulk added {} questions to applicationId={}", result.size(), applicationId);
        return result;
    }
}
