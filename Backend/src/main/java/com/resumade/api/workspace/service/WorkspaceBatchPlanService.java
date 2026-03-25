package com.resumade.api.workspace.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.experience.domain.Experience;
import com.resumade.api.experience.domain.ExperienceRepository;
import com.resumade.api.workspace.domain.Application;
import com.resumade.api.workspace.domain.ApplicationRepository;
import com.resumade.api.workspace.dto.BatchPlanRequest;
import com.resumade.api.workspace.dto.BatchPlanResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkspaceBatchPlanService {

    private static final String RESPONSES_API_URL = "https://api.openai.com/v1/responses";
    private static final Map<String, Object> PLAN_RESPONSE_SCHEMA = createPlanResponseSchema();
    private static final int MAX_EXPERIENCE_DESCRIPTION_CHARS = 240;
    private static final int MAX_EXPERIENCE_RAW_CHARS = 380;
    private static final int MAX_CURRENT_DRAFT_CHARS = 220;
    private static final String SYSTEM_PROMPT = """
            You design a batch-writing strategy for Korean self-introduction questions.
            Return JSON only and follow the schema exactly.
            Optimize for question-to-question differentiation.
            Important: project-title overlap is allowed.
            The real overlap risk is reusing the same detailed topic inside a project: the same technical decision, failure, troubleshooting point, architecture tradeoff, metric cluster, learning point, or action-result arc.
            If the same project appears in multiple questions, assign clearly different detail slices and different lessons.
            If a question naturally benefits from multiple projects, you may assign 2 to 3 projects, but each project must contribute a distinct point rather than a loose list.
            Prefer concrete, interview-verifiable focus details over broad themes.
            Avoid generic plans like "show collaboration" unless paired with concrete evidence anchors.
            Keep coverageSummary concise.
            Keep each list short and specific.
            """;

    private final ApplicationRepository applicationRepository;
    private final ExperienceRepository experienceRepository;
    private final ObjectMapper objectMapper;

    @Value("${openai.api.key:demo}")
    private String apiKey;

    @Value("${openai.api.timeout:PT5M}")
    private Duration timeout;

    @Value("${openai.models.workspace-plan:${openai.models.workspace-draft:gpt-5-mini}}")
    private String modelName;

    public BatchPlanResponse createPlan(BatchPlanRequest request) {
        if (request == null || request.getApplicationId() == null) {
            throw new IllegalArgumentException("applicationId is required");
        }
        if (request.getQuestions() == null || request.getQuestions().isEmpty()) {
            throw new IllegalArgumentException("questions are required");
        }

        Application application = applicationRepository.findById(request.getApplicationId())
                .orElseThrow(() -> new IllegalArgumentException("Application not found: " + request.getApplicationId()));

        List<BatchPlanRequest.QuestionSnapshot> questions = request.getQuestions().stream()
                .filter(Objects::nonNull)
                .filter(question -> question.getQuestionId() != null)
                .filter(question -> question.getTitle() != null && !question.getTitle().isBlank())
                .toList();

        if (questions.isEmpty()) {
            throw new IllegalArgumentException("At least one valid question is required");
        }

        List<Experience> experiences = experienceRepository.findAll();
        if (apiKey == null || apiKey.isBlank() || "demo".equalsIgnoreCase(apiKey.trim())) {
            log.warn("OpenAI API key missing for workspace batch plan. Falling back to heuristic planning.");
            return buildHeuristicPlan(questions, experiences);
        }

        try {
            JsonNode response = requestPlanFromOpenAi(application, questions, experiences);
            BatchPlanAiResponse parsed = parsePlanResponse(response);
            return toResponse(parsed, questions);
        } catch (Exception e) {
            log.warn("Workspace batch planning failed. Falling back to heuristic plan. model={}", modelName, e);
            return buildHeuristicPlan(questions, experiences);
        }
    }

    private JsonNode requestPlanFromOpenAi(
            Application application,
            List<BatchPlanRequest.QuestionSnapshot> questions,
            List<Experience> experiences
    ) {
        RestTemplate restTemplate = buildRestTemplate(timeout);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey.trim());

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", modelName);
        requestBody.put("input", List.of(
                message("system", SYSTEM_PROMPT),
                message("user", buildUserPrompt(application, questions, experiences))
        ));
        requestBody.put("max_output_tokens", 2400);
        requestBody.put("reasoning", buildReasoningConfig());
        requestBody.put("text", buildTextConfig());

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        JsonNode response = restTemplate.postForObject(RESPONSES_API_URL, entity, JsonNode.class);
        if (response == null) {
            throw new IllegalStateException("Responses API returned no body");
        }
        return response;
    }

    private String buildUserPrompt(
            Application application,
            List<BatchPlanRequest.QuestionSnapshot> questions,
            List<Experience> experiences
    ) {
        String questionsBlock = questions.stream()
                .map(this::formatQuestionBlock)
                .collect(Collectors.joining("\n\n"));

        String experiencesBlock = experiences.isEmpty()
                ? "[EXPERIENCE]\nNo experience records available."
                : experiences.stream()
                        .map(this::formatExperienceBlock)
                        .collect(Collectors.joining("\n\n"));

        return """
                Company: %s
                Position: %s

                Company fit context:
                - AI insight: %s
                - Company research: %s
                - Raw JD: %s

                Questions:
                %s

                Experience vault:
                %s

                Planning rules:
                - Same project may appear in multiple questions when the detailed topic is different.
                - The main anti-overlap unit is detail-level evidence, not project name.
                - Spread out technical topics, learned lessons, and result evidence across questions.
                - If one question uses a project's architecture choice, another question should not reuse that same choice unless there is no credible alternative.
                - If one question uses a project's troubleshooting lesson, another question may still use the same project for leadership, collaboration, prioritization, or a different technical sub-problem.
                - Respect each question's own intent and any user directive already attached to it.
                - Avoid assigning the exact same lesson, metric cluster, or evidence arc to multiple questions.
                - Return assignments in the same order as the question list.
                """.formatted(
                safe(application.getCompanyName()),
                safe(application.getPosition()),
                safe(snippet(application.getAiInsight(), 700)),
                safe(snippet(application.getCompanyResearch(), 1000)),
                safe(snippet(application.getRawJd(), 1000)),
                questionsBlock,
                experiencesBlock
        );
    }

    private String formatQuestionBlock(BatchPlanRequest.QuestionSnapshot question) {
        return """
                [QUESTION]
                questionId: %d
                title: %s
                maxLength: %s
                current user directive: %s
                current strategy directive: %s
                current draft snippet: %s
                """.formatted(
                question.getQuestionId(),
                safe(question.getTitle()),
                question.getMaxLength() == null ? "unknown" : question.getMaxLength(),
                safe(snippet(question.getUserDirective(), 320)),
                safe(snippet(question.getBatchStrategyDirective(), 320)),
                safe(snippet(preferredDraft(question), MAX_CURRENT_DRAFT_CHARS))
        );
    }

    private String formatExperienceBlock(Experience experience) {
        return """
                [EXPERIENCE]
                title: %s
                role: %s
                period: %s
                category: %s
                tech stack: %s
                metrics: %s
                summary: %s
                raw details: %s
                """.formatted(
                safe(experience.getTitle()),
                safe(experience.getRole()),
                safe(experience.getPeriod()),
                safe(experience.getCategory()),
                safe(joinJsonArray(experience.getTechStack())),
                safe(joinJsonArray(experience.getMetrics())),
                safe(snippet(experience.getDescription(), MAX_EXPERIENCE_DESCRIPTION_CHARS)),
                safe(snippet(experience.getRawContent(), MAX_EXPERIENCE_RAW_CHARS))
        );
    }

    private BatchPlanAiResponse parsePlanResponse(JsonNode response) throws IOException {
        String outputText = extractOutputText(response).trim();
        if (outputText.isBlank()) {
            throw new IllegalStateException("Responses API batch plan payload was empty");
        }
        return objectMapper.readValue(outputText, BatchPlanAiResponse.class);
    }

    private BatchPlanResponse toResponse(
            BatchPlanAiResponse parsed,
            List<BatchPlanRequest.QuestionSnapshot> questions
    ) {
        Map<Long, BatchPlanRequest.QuestionSnapshot> questionMap = questions.stream()
                .collect(Collectors.toMap(
                        BatchPlanRequest.QuestionSnapshot::getQuestionId,
                        question -> question,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        List<BatchPlanResponse.Assignment> assignments = new ArrayList<>();
        for (BatchPlanAiAssignment assignment : parsed.assignments) {
            BatchPlanRequest.QuestionSnapshot question = questionMap.get(assignment.questionId);
            if (question == null) {
                continue;
            }

            List<String> primaryExperiences = normalizeList(assignment.primaryExperiences);
            List<String> focusDetails = normalizeList(assignment.focusDetails);
            List<String> learningPoints = normalizeList(assignment.learningPoints);
            List<String> avoidDetails = normalizeList(assignment.avoidDetails);
            String angle = safe(assignment.angle);

            assignments.add(BatchPlanResponse.Assignment.builder()
                    .questionId(question.getQuestionId())
                    .questionTitle(question.getTitle())
                    .primaryExperiences(primaryExperiences)
                    .angle(angle)
                    .focusDetails(focusDetails)
                    .learningPoints(learningPoints)
                    .avoidDetails(avoidDetails)
                    .reasoning(safe(assignment.reasoning))
                    .directivePrefix(buildDirectivePrefix(primaryExperiences, angle, focusDetails, learningPoints, avoidDetails))
                    .build());
        }

        if (assignments.isEmpty()) {
            return buildHeuristicPlan(questions, experienceRepository.findAll());
        }

        return BatchPlanResponse.builder()
                .coverageSummary(safe(parsed.coverageSummary))
                .globalGuardrails(normalizeList(parsed.globalGuardrails))
                .model(modelName)
                .assignments(assignments)
                .build();
    }

    private BatchPlanResponse buildHeuristicPlan(
            List<BatchPlanRequest.QuestionSnapshot> questions,
            List<Experience> experiences
    ) {
        List<String> experienceTitles = experiences.stream()
                .map(Experience::getTitle)
                .filter(Objects::nonNull)
                .filter(title -> !title.isBlank())
                .toList();

        List<BatchPlanResponse.Assignment> assignments = new ArrayList<>();
        for (int index = 0; index < questions.size(); index++) {
            BatchPlanRequest.QuestionSnapshot question = questions.get(index);
            List<String> chosenExperiences = experienceTitles.isEmpty()
                    ? List.of("관련 경험 선택 필요")
                    : List.of(experienceTitles.get(index % experienceTitles.size()));

            List<String> focusDetails = inferFallbackFocusDetails(question.getTitle(), experiences, index);
            List<String> learningPoints = inferFallbackLearningPoints(question.getTitle());
            List<String> avoidDetails = List.of(
                    "다른 문항과 동일한 기술 결정 설명 반복 금지",
                    "다른 문항과 동일한 배운점 문장 반복 금지"
            );

            assignments.add(BatchPlanResponse.Assignment.builder()
                    .questionId(question.getQuestionId())
                    .questionTitle(question.getTitle())
                    .primaryExperiences(chosenExperiences)
                    .angle(inferFallbackAngle(question.getTitle()))
                    .focusDetails(focusDetails)
                    .learningPoints(learningPoints)
                    .avoidDetails(avoidDetails)
                    .reasoning("Fallback heuristic plan generated because structured planning was unavailable.")
                    .directivePrefix(buildDirectivePrefix(
                            chosenExperiences,
                            inferFallbackAngle(question.getTitle()),
                            focusDetails,
                            learningPoints,
                            avoidDetails))
                    .build());
        }

        return BatchPlanResponse.builder()
                .coverageSummary("문항별로 세부 기술과 배운점이 겹치지 않도록 기본 분산 전략을 적용했습니다.")
                .globalGuardrails(List.of(
                        "같은 프로젝트 재사용은 허용하되 세부 기술 포인트는 분리",
                        "동일한 배운점과 결과 서술 반복 금지",
                        "문항별 첫 문장 주장과 증거 축 분리"
                ))
                .model(modelName + " (heuristic fallback)")
                .assignments(assignments)
                .build();
    }

    private List<String> inferFallbackFocusDetails(String questionTitle, List<Experience> experiences, int index) {
        List<String> candidates = new ArrayList<>();
        if (!experiences.isEmpty()) {
            Experience selected = experiences.get(index % experiences.size());
            candidates.addAll(normalizeList(readJsonArray(selected.getTechStack())));
            if (selected.getDescription() != null && !selected.getDescription().isBlank()) {
                candidates.add(snippet(selected.getDescription(), 80));
            }
        }

        String normalized = safe(questionTitle).toLowerCase(Locale.ROOT);
        if (normalized.contains("협업") || normalized.contains("팀")) {
            candidates.add("협업 과정에서 맡은 역할과 조율 방식");
        }
        if (normalized.contains("성장") || normalized.contains("배운")) {
            candidates.add("실패나 시행착오 뒤에 바뀐 판단 기준");
        }
        if (normalized.contains("지원동기") || normalized.contains("왜")) {
            candidates.add("경험에서 직무 적합성으로 연결되는 기술 선택 기준");
        }

        return normalizeList(candidates).stream().limit(3).toList();
    }

    private List<String> inferFallbackLearningPoints(String questionTitle) {
        String normalized = safe(questionTitle).toLowerCase(Locale.ROOT);
        if (normalized.contains("협업") || normalized.contains("팀")) {
            return List.of("기술 설명을 팀 상황에 맞게 조정하는 능력");
        }
        if (normalized.contains("성장") || normalized.contains("배운")) {
            return List.of("문제 원인을 구조적으로 다시 보는 습관");
        }
        if (normalized.contains("지원동기") || normalized.contains("왜")) {
            return List.of("기술 선택을 사용자 가치와 연결하는 기준");
        }
        return List.of("문제 해결 경험을 직무 역량으로 번역하는 시각");
    }

    private String inferFallbackAngle(String questionTitle) {
        String normalized = safe(questionTitle).toLowerCase(Locale.ROOT);
        if (normalized.contains("협업") || normalized.contains("팀")) {
            return "협업 과정에서 기술 판단과 조율 역량을 증명하는 각도";
        }
        if (normalized.contains("성장") || normalized.contains("배운")) {
            return "시행착오를 통해 판단 기준이 정교해진 성장 각도";
        }
        if (normalized.contains("지원동기") || normalized.contains("왜")) {
            return "과거 경험을 직무 적합성으로 연결하는 지원동기 각도";
        }
        return "핵심 문제를 해결하며 만든 판단과 실행의 차별점을 보여주는 각도";
    }

    private String buildDirectivePrefix(
            List<String> primaryExperiences,
            String angle,
            List<String> focusDetails,
            List<String> learningPoints,
            List<String> avoidDetails
    ) {
        List<String> lines = new ArrayList<>();
        lines.add("[Batch strategy]");
        lines.add("Use this strategy together with any user directive below.");
        lines.add("Project title overlap across questions is allowed only when the detailed topic is different.");
        if (!primaryExperiences.isEmpty()) {
            lines.add("Primary experiences: " + String.join(", ", primaryExperiences));
        }
        lines.add("Angle: " + safe(angle));
        if (!focusDetails.isEmpty()) {
            lines.add("Focus details: " + String.join(" | ", focusDetails));
        }
        if (!learningPoints.isEmpty()) {
            lines.add("Learning points to prove: " + String.join(" | ", learningPoints));
        }
        if (!avoidDetails.isEmpty()) {
            lines.add("Avoid reusing these details from other questions: " + String.join(" | ", avoidDetails));
        }
        lines.add("If the same project appears elsewhere, change the sub-problem, technical decision, lesson, and evidence arc.");
        lines.add("[/Batch strategy]");
        return String.join("\n", lines);
    }

    private Map<String, Object> buildTextConfig() {
        Map<String, Object> text = new LinkedHashMap<>();
        if (modelName != null && modelName.startsWith("gpt-5")) {
            text.put("verbosity", "low");
        }

        Map<String, Object> format = new LinkedHashMap<>();
        format.put("type", "json_schema");
        format.put("name", "workspace_batch_plan");
        format.put("strict", true);
        format.put("schema", PLAN_RESPONSE_SCHEMA);
        text.put("format", format);
        return text;
    }

    private Map<String, Object> buildReasoningConfig() {
        if (modelName == null || modelName.isBlank()) {
            return Map.of();
        }

        String normalized = modelName.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("gpt-5")) {
            return Map.of("effort", "low");
        }
        return Map.of();
    }

    private Map<String, Object> message(String role, String text) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", List.of(Map.of(
                "type", "input_text",
                "text", text
        )));
        return message;
    }

    private String extractOutputText(JsonNode response) {
        JsonNode direct = response.get("output_text");
        if (direct != null) {
            String directText = extractNodeText(direct);
            if (!directText.isBlank()) {
                return directText;
            }
        }

        StringBuilder builder = new StringBuilder();
        JsonNode outputs = response.path("output");
        if (outputs.isArray()) {
            outputs.forEach(item -> {
                appendIfPresent(builder, item.get("arguments"));
                appendIfPresent(builder, item.get("json"));
                appendIfPresent(builder, item.get("parsed"));

                JsonNode content = item.path("content");
                if (!content.isArray()) {
                    return;
                }
                content.forEach(part -> {
                    appendIfPresent(builder, part.get("text"));
                    appendIfPresent(builder, part.get("output_text"));
                    appendIfPresent(builder, part.get("json"));
                    appendIfPresent(builder, part.get("parsed"));
                    appendIfPresent(builder, part.get("arguments"));
                });
            });
        }

        if (builder.length() == 0) {
            throw new IllegalStateException("Responses API returned no parseable batch plan output");
        }
        return builder.toString();
    }

    private void appendIfPresent(StringBuilder builder, JsonNode node) {
        String extracted = extractNodeText(node);
        if (extracted.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append('\n');
        }
        builder.append(extracted);
    }

    private String extractNodeText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText("");
        }
        JsonNode value = node.get("value");
        if (value != null && value.isTextual()) {
            return value.asText("");
        }
        JsonNode text = node.get("text");
        if (text != null && text != node) {
            String nested = extractNodeText(text);
            if (!nested.isBlank()) {
                return nested;
            }
        }
        if (node.isObject() || node.isArray()) {
            return node.toString();
        }
        return node.asText("");
    }

    private static RestTemplate buildRestTemplate(Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, timeout.toMillis()));
        factory.setConnectTimeout(timeoutMillis);
        factory.setReadTimeout(timeoutMillis);
        return new RestTemplate(factory);
    }

    private List<String> normalizeList(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        return raw.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(5)
                .toList();
    }

    private String preferredDraft(BatchPlanRequest.QuestionSnapshot question) {
        if (question.getWashedKr() != null && !question.getWashedKr().isBlank()) {
            return question.getWashedKr();
        }
        return question.getContent();
    }

    private String joinJsonArray(String raw) {
        List<String> values = readJsonArray(raw);
        if (values.isEmpty()) {
            return raw == null ? "" : raw;
        }
        return String.join(", ", values);
    }

    private List<String> readJsonArray(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<String>>() {
            });
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String snippet(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "None" : value.trim();
    }

    private static Map<String, Object> createPlanResponseSchema() {
        Map<String, Object> assignmentProperties = new LinkedHashMap<>();
        assignmentProperties.put("questionId", Map.of("type", "integer"));
        assignmentProperties.put("questionTitle", Map.of("type", "string"));
        assignmentProperties.put("primaryExperiences", Map.of(
                "type", "array",
                "items", Map.of("type", "string")));
        assignmentProperties.put("angle", Map.of("type", "string"));
        assignmentProperties.put("focusDetails", Map.of(
                "type", "array",
                "items", Map.of("type", "string")));
        assignmentProperties.put("learningPoints", Map.of(
                "type", "array",
                "items", Map.of("type", "string")));
        assignmentProperties.put("avoidDetails", Map.of(
                "type", "array",
                "items", Map.of("type", "string")));
        assignmentProperties.put("reasoning", Map.of("type", "string"));

        Map<String, Object> assignmentSchema = new LinkedHashMap<>();
        assignmentSchema.put("type", "object");
        assignmentSchema.put("additionalProperties", false);
        assignmentSchema.put("properties", assignmentProperties);
        assignmentSchema.put("required", List.of(
                "questionId",
                "questionTitle",
                "primaryExperiences",
                "angle",
                "focusDetails",
                "learningPoints",
                "avoidDetails",
                "reasoning"
        ));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("coverageSummary", Map.of("type", "string"));
        properties.put("globalGuardrails", Map.of(
                "type", "array",
                "items", Map.of("type", "string")));
        properties.put("assignments", Map.of(
                "type", "array",
                "items", assignmentSchema));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", properties);
        schema.put("required", List.of("coverageSummary", "globalGuardrails", "assignments"));
        return schema;
    }

    private static class BatchPlanAiResponse {
        public String coverageSummary;
        public List<String> globalGuardrails;
        public List<BatchPlanAiAssignment> assignments;
    }

    private static class BatchPlanAiAssignment {
        public Long questionId;
        public String questionTitle;
        public List<String> primaryExperiences;
        public String angle;
        public List<String> focusDetails;
        public List<String> learningPoints;
        public List<String> avoidDetails;
        public String reasoning;
    }
}
