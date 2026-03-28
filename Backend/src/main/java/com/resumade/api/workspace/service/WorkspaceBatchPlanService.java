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
            You are a senior cover-letter strategy architect for Korean self-introduction questions.
            Your task: design a differentiated, interview-verifiable writing strategy for EVERY question in one batch.
            Work through all four reasoning steps below IN ORDER before writing any assignment.
            Return valid JSON only. Follow the schema exactly.

            <Step1_IntentClassification>
            Before assigning any experience, classify EACH question's core evaluation intent.
            Use EXACTLY ONE tag from this list per question:

              [도메인 관심도]   – evaluates passion for the industry/company domain (지원동기, 입사 후 포부)
              [직무 하드스킬]   – evaluates technical depth and engineering judgment (기술 경험, 프로젝트)
              [소프트스킬/협업] – evaluates communication, conflict resolution, teamwork
              [직업윤리]        – evaluates values, integrity, failure/mistake handling
              [성장/학습]       – evaluates self-awareness, learning agility, growth trajectory

            Rules:
            - If a question title mentions 지원동기, 관심, 입사 후 → tag [도메인 관심도]
            - If a question title mentions 협업, 갈등, 팀, 소통 → tag [소프트스킬/협업]
            - For [도메인 관심도] questions: NEVER lead with technical stack.
              The answer must start from company domain interest, then bridge to your technical evidence.
            - For [직업윤리] questions: NEVER use performance metrics as the main story. Use a values-driven narrative.
            Write the intentTag and a 1-sentence intentRationale for EVERY question before moving to Step 2.
            </Step1_IntentClassification>

            <Step2_FacetMapping>
            Do NOT map an entire project to a question.
            Instead, identify a SPECIFIC EVENT or DECISION inside that project — a "facet".

            A valid facet looks like:
              ✅ "Tikkle – 인프라팀과 배포 지연 원인에 대한 이견 조율 과정"
              ✅ "CodeArena – Judge 서버 부하 급증 시 Circuit Breaker 전환 결정"
              ✅ "Fastats – MySQL Full-Text vs Elasticsearch 선택 기준 수립"

            An invalid facet looks like:
              ❌ "Tikkle 프로젝트 전반"
              ❌ "CodeArena에서의 성능 개선 경험"

            For EACH assignment, produce 1–3 experienceFacets (event-level strings).
            If the question's intentTag is [도메인 관심도], the facet must point to a moment
            where you realized alignment between your work and this company's business domain.
            </Step2_FacetMapping>

            <Step3_DomainBridge>
            For EVERY assignment, write a domainBridge: a 1–2 sentence logical bridge that connects
            your technical achievement to this specific company's business value.

            Format: "[technical outcome] → [why it matters for THIS company's domain]"

            Examples:
              "검색 응답속도 95ms 단축 → 식품안전정보원의 국민 데이터 접근성과 신뢰도 직결"
              "결제 API 타임아웃 방어 로직 → 금융 서비스 SLA 달성과 고객 신뢰 유지에 직결"

            If the question is [소프트스킬/협업], the bridge must connect the collaboration outcome
            to team velocity or product quality, not technical architecture.
            </Step3_DomainBridge>

            <Step4_AntiOverlapValidation>
            After drafting ALL assignments, perform a cross-check:

            1. List every tech topic used as a CORE element across all assignments (e.g., Redis, Kafka, JPA N+1).
            2. List every lesson/learning used as a CORE takeaway across all assignments.
            3. List every metric cluster used as primary evidence across all assignments.

            If ANY tech topic, lesson, or metric appears as the core element in 2 or more assignments:
              → Reassign one of the conflicting questions to use a different facet/topic/lesson.
              → If reassignment is impossible, mark it in overlapValidation.conflictPairs
                and explain your resolution in overlapValidation.resolution.

            The goal: every question must have a unique "evidence fingerprint."
            A reader who sees all answers together must feel each answer is about a different dimension.

            Set overlapValidation.isClean = true only when you are confident no core element repeats.
            </Step4_AntiOverlapValidation>

            <Global_Rules>
            - userDirective per question always overrides any default rule.
            - Default: assign exactly 1 primary experience per question (depth over breadth).
              Exception: assign 2–3 only when userDirective explicitly requests a list-style answer.
            - Same project name is ALLOWED to appear in multiple questions IF the facet is different.
            - Keep all list fields short and concrete. Avoid generic plans.
            - coverageSummary must describe the distribution logic in 1–2 Korean sentences.
            </Global_Rules>
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
                - userDirective always takes the highest priority. Read each question's userDirective first and honor it exactly before applying any default rule.
                - Default: assign exactly 1 primary experience per question for depth and focus. Only assign multiple experiences when the question's userDirective explicitly requests a list-style answer or multiple experiences.
                - Same project may appear in multiple questions when the detailed topic is different.
                - The main anti-overlap unit is detail-level evidence, not project name.
                - Spread out technical topics, learned lessons, and result evidence across questions.
                - If one question uses a project's architecture choice, another question should not reuse that same choice unless there is no credible alternative.
                - If one question uses a project's troubleshooting lesson, another question may still use the same project for leadership, collaboration, prioritization, or a different technical sub-problem.
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

            assignments.add(BatchPlanResponse.Assignment.builder()
                    .questionId(question.getQuestionId())
                    .questionTitle(question.getTitle())
                    .questionIntentTag(safe(assignment.questionIntentTag))
                    .intentRationale(safe(assignment.intentRationale))
                    .primaryExperiences(normalizeList(assignment.primaryExperiences))
                    .experienceFacets(normalizeList(assignment.experienceFacets))
                    .domainBridge(safe(assignment.domainBridge))
                    .angle(safe(assignment.angle))
                    .focusDetails(normalizeList(assignment.focusDetails))
                    .learningPoints(normalizeList(assignment.learningPoints))
                    .avoidDetails(normalizeList(assignment.avoidDetails))
                    .reasoning(safe(assignment.reasoning))
                    .directivePrefix(buildDirectivePrefix(assignment))
                    .build());
        }

        if (assignments.isEmpty()) {
            return buildHeuristicPlan(questions, experienceRepository.findAll());
        }

        BatchPlanAiOverlapValidation ov = parsed.overlapValidation;
        BatchPlanResponse.OverlapValidation overlapValidation = ov == null
                ? BatchPlanResponse.OverlapValidation.builder()
                        .isClean(true).conflictPairs(List.of()).resolution("").build()
                : BatchPlanResponse.OverlapValidation.builder()
                        .isClean(ov.isClean)
                        .conflictPairs(normalizeList(ov.conflictPairs))
                        .resolution(safe(ov.resolution))
                        .build();

        return BatchPlanResponse.builder()
                .coverageSummary(safe(parsed.coverageSummary))
                .globalGuardrails(normalizeList(parsed.globalGuardrails))
                .overlapValidation(overlapValidation)
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

            String intentTag = inferFallbackIntentTag(question.getTitle());
            List<String> focusDetails = inferFallbackFocusDetails(question.getTitle(), experiences, index);
            List<String> learningPoints = inferFallbackLearningPoints(question.getTitle());
            List<String> avoidDetails = List.of(
                    "다른 문항과 동일한 기술 결정 설명 반복 금지",
                    "다른 문항과 동일한 배운점 문장 반복 금지"
            );
            String angle = inferFallbackAngle(question.getTitle());

            BatchPlanAiAssignment stub = new BatchPlanAiAssignment();
            stub.questionIntentTag = intentTag;
            stub.intentRationale = "Heuristic fallback: classified by keyword matching.";
            stub.primaryExperiences = chosenExperiences;
            stub.experienceFacets = List.of();
            stub.domainBridge = "";
            stub.angle = angle;
            stub.focusDetails = focusDetails;
            stub.learningPoints = learningPoints;
            stub.avoidDetails = avoidDetails;

            assignments.add(BatchPlanResponse.Assignment.builder()
                    .questionId(question.getQuestionId())
                    .questionTitle(question.getTitle())
                    .questionIntentTag(intentTag)
                    .intentRationale("Heuristic fallback: classified by keyword matching.")
                    .primaryExperiences(chosenExperiences)
                    .experienceFacets(List.of())
                    .domainBridge("")
                    .angle(angle)
                    .focusDetails(focusDetails)
                    .learningPoints(learningPoints)
                    .avoidDetails(avoidDetails)
                    .reasoning("Fallback heuristic plan generated because structured planning was unavailable.")
                    .directivePrefix(buildDirectivePrefix(stub))
                    .build());
        }

        return BatchPlanResponse.builder()
                .coverageSummary("문항별로 세부 기술과 배운점이 겹치지 않도록 기본 분산 전략을 적용했습니다.")
                .globalGuardrails(List.of(
                        "같은 프로젝트 재사용은 허용하되 세부 기술 포인트는 분리",
                        "동일한 배운점과 결과 서술 반복 금지",
                        "문항별 첫 문장 주장과 증거 축 분리"
                ))
                .overlapValidation(BatchPlanResponse.OverlapValidation.builder()
                        .isClean(true).conflictPairs(List.of()).resolution("Heuristic fallback — no validation performed.").build())
                .model(modelName + " (heuristic fallback)")
                .assignments(assignments)
                .build();
    }

    private String inferFallbackIntentTag(String questionTitle) {
        String normalized = safe(questionTitle).toLowerCase(Locale.ROOT);
        if (isMotivationQuestion(normalized)) {
            return "[도메인 관심도]";
        }
        if (isCollaborationQuestion(normalized)) {
            return "[소프트스킬/협업]";
        }
        if (isGrowthQuestion(normalized)) {
            return "[성장/학습]";
        }
        if (isEthicsQuestion(normalized)) {
            return "[직업윤리]";
        }
        if (isCapabilityQuestion(normalized)) {
            return "[직무 하드스킬]";
        }
        return "[직무 하드스킬]";
    }

    // ── Intent keyword helpers ────────────────────────────────────────────────

    private boolean isMotivationQuestion(String normalized) {
        return normalized.contains("지원동기")
                || normalized.contains("지원 동기")
                || normalized.contains("동기와")
                || normalized.contains("동기를")
                || normalized.contains("동기가")
                || normalized.contains("입사 후")
                || normalized.contains("입사후")
                || normalized.contains("포부")
                || normalized.contains("관심")
                || normalized.contains("왜 지원");
    }

    private boolean isCollaborationQuestion(String normalized) {
        return normalized.contains("협업")
                || normalized.contains("갈등")
                || normalized.contains("팀워크")
                || normalized.contains("팀 프로젝트")
                || normalized.contains("소통")
                || normalized.contains("커뮤니케이션");
    }

    private boolean isGrowthQuestion(String normalized) {
        return normalized.contains("성장")
                || normalized.contains("배운")
                || normalized.contains("학습")
                || normalized.contains("발전")
                || normalized.contains("개선한");
    }

    private boolean isEthicsQuestion(String normalized) {
        return normalized.contains("윤리")
                || normalized.contains("실패")
                || normalized.contains("어려움")
                || normalized.contains("극복");
    }

    private boolean isCapabilityQuestion(String normalized) {
        return normalized.contains("역량")
                || normalized.contains("경험 및 경력")
                || normalized.contains("직무수행")
                || normalized.contains("수행계획")
                || normalized.contains("보유한")
                || normalized.contains("강점")
                || normalized.contains("전문성");
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
        if (isCollaborationQuestion(normalized)) {
            candidates.add("협업 과정에서 맡은 역할과 조율 방식");
        } else if (isGrowthQuestion(normalized)) {
            candidates.add("실패나 시행착오 뒤에 바뀐 판단 기준");
        } else if (isMotivationQuestion(normalized)) {
            candidates.add("지원 기업 도메인과 내 경험이 맞닿은 구체적 접점");
        } else if (isCapabilityQuestion(normalized)) {
            candidates.add("직무에서 즉시 발휘 가능한 기술 판단과 실행 이력");
        }

        return normalizeList(candidates).stream().limit(3).toList();
    }

    private List<String> inferFallbackLearningPoints(String questionTitle) {
        String normalized = safe(questionTitle).toLowerCase(Locale.ROOT);
        if (isCollaborationQuestion(normalized)) {
            return List.of("기술 설명을 팀 상황에 맞게 조정하는 능력");
        }
        if (isGrowthQuestion(normalized)) {
            return List.of("문제 원인을 구조적으로 다시 보는 습관");
        }
        if (isMotivationQuestion(normalized)) {
            return List.of("기술 선택을 사용자 가치와 연결하는 기준");
        }
        if (isCapabilityQuestion(normalized)) {
            return List.of("기술 역량이 실제 비즈니스 임팩트로 이어진 경로");
        }
        return List.of("문제 해결 경험을 직무 역량으로 번역하는 시각");
    }

    private String inferFallbackAngle(String questionTitle) {
        String normalized = safe(questionTitle).toLowerCase(Locale.ROOT);
        if (isMotivationQuestion(normalized)) {
            return "이 기업의 도메인과 내 경험이 맞닿은 접점에서 출발하는 지원동기 각도";
        }
        if (isCollaborationQuestion(normalized)) {
            return "협업 과정에서 기술 판단과 조율 역량을 증명하는 각도";
        }
        if (isGrowthQuestion(normalized)) {
            return "시행착오를 통해 판단 기준이 정교해진 성장 각도";
        }
        if (isEthicsQuestion(normalized)) {
            return "가치 판단이 흔들린 순간 내린 결정과 그 기준을 보여주는 각도";
        }
        if (isCapabilityQuestion(normalized)) {
            return "보유 역량과 실행 이력을 직무 수행 청사진으로 연결하는 역량 어필 각도";
        }
        return "핵심 문제를 해결하며 만든 판단과 실행의 차별점을 보여주는 각도";
    }

    private String buildDirectivePrefix(BatchPlanAiAssignment a) {
        List<String> lines = new ArrayList<>();
        lines.add("[Batch Strategy]");
        lines.add("Use with any user directive. User directive takes higher priority.");

        String intentTag = safe(a.questionIntentTag);
        if (!"None".equals(intentTag)) {
            lines.add("Question intent: " + intentTag);
            if (intentTag.contains("도메인 관심도")) {
                lines.add("→ Start from domain/company interest. Do NOT open with tech stack.");
            } else if (intentTag.contains("직업윤리")) {
                lines.add("→ Focus on values and judgment. Do NOT use performance metrics as the main story.");
            } else if (intentTag.contains("소프트스킬")) {
                lines.add("→ Lead with human dynamics and interpersonal process, then connect to outcome.");
            }
        }

        List<String> primaryExperiences = normalizeList(a.primaryExperiences);
        if (!primaryExperiences.isEmpty()) {
            lines.add("Primary experience: " + String.join(", ", primaryExperiences));
        }

        List<String> experienceFacets = normalizeList(a.experienceFacets);
        if (!experienceFacets.isEmpty()) {
            lines.add("Use this specific facet (event-level, not the whole project): "
                    + String.join(" | ", experienceFacets));
        }

        String domainBridge = safe(a.domainBridge);
        if (!"None".equals(domainBridge)) {
            lines.add("Domain bridge (embed this logic into your answer): " + domainBridge);
        }

        String angle = safe(a.angle);
        if (!"None".equals(angle)) {
            lines.add("Angle: " + angle);
        }

        List<String> focusDetails = normalizeList(a.focusDetails);
        if (!focusDetails.isEmpty()) {
            lines.add("Focus details: " + String.join(" | ", focusDetails));
        }

        List<String> learningPoints = normalizeList(a.learningPoints);
        if (!learningPoints.isEmpty()) {
            lines.add("Learning points to prove: " + String.join(" | ", learningPoints));
        }

        List<String> avoidDetails = normalizeList(a.avoidDetails);
        if (!avoidDetails.isEmpty()) {
            lines.add("Avoid reusing from other questions: " + String.join(" | ", avoidDetails));
        }

        lines.add("If the same project appears elsewhere, change the sub-problem, technical decision, lesson, and evidence arc.");
        lines.add("[/Batch Strategy]");
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
        // --- Assignment schema ---
        Map<String, Object> assignmentProps = new LinkedHashMap<>();
        assignmentProps.put("questionId",        Map.of("type", "integer"));
        assignmentProps.put("questionTitle",      Map.of("type", "string"));
        assignmentProps.put("questionIntentTag",  Map.of("type", "string"));
        assignmentProps.put("intentRationale",    Map.of("type", "string"));
        assignmentProps.put("primaryExperiences", arrayOfString());
        assignmentProps.put("experienceFacets",   arrayOfString());
        assignmentProps.put("domainBridge",       Map.of("type", "string"));
        assignmentProps.put("angle",              Map.of("type", "string"));
        assignmentProps.put("focusDetails",       arrayOfString());
        assignmentProps.put("learningPoints",     arrayOfString());
        assignmentProps.put("avoidDetails",       arrayOfString());
        assignmentProps.put("reasoning",          Map.of("type", "string"));

        Map<String, Object> assignmentSchema = new LinkedHashMap<>();
        assignmentSchema.put("type", "object");
        assignmentSchema.put("additionalProperties", false);
        assignmentSchema.put("properties", assignmentProps);
        assignmentSchema.put("required", List.of(
                "questionId", "questionTitle",
                "questionIntentTag", "intentRationale",
                "primaryExperiences", "experienceFacets", "domainBridge",
                "angle", "focusDetails", "learningPoints", "avoidDetails", "reasoning"
        ));

        // --- OverlapValidation schema ---
        Map<String, Object> overlapProps = new LinkedHashMap<>();
        overlapProps.put("isClean",       Map.of("type", "boolean"));
        overlapProps.put("conflictPairs", arrayOfString());
        overlapProps.put("resolution",    Map.of("type", "string"));

        Map<String, Object> overlapSchema = new LinkedHashMap<>();
        overlapSchema.put("type", "object");
        overlapSchema.put("additionalProperties", false);
        overlapSchema.put("properties", overlapProps);
        overlapSchema.put("required", List.of("isClean", "conflictPairs", "resolution"));

        // --- Root schema ---
        Map<String, Object> rootProps = new LinkedHashMap<>();
        rootProps.put("coverageSummary",   Map.of("type", "string"));
        rootProps.put("globalGuardrails",  arrayOfString());
        rootProps.put("overlapValidation", overlapSchema);
        rootProps.put("assignments", Map.of(
                "type", "array",
                "items", assignmentSchema));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", rootProps);
        schema.put("required", List.of(
                "coverageSummary", "globalGuardrails", "overlapValidation", "assignments"
        ));
        return schema;
    }

    private static Map<String, Object> arrayOfString() {
        return Map.of("type", "array", "items", Map.of("type", "string"));
    }

    private static class BatchPlanAiResponse {
        public String coverageSummary;
        public List<String> globalGuardrails;
        public BatchPlanAiOverlapValidation overlapValidation;
        public List<BatchPlanAiAssignment> assignments;
    }

    private static class BatchPlanAiOverlapValidation {
        public boolean isClean;
        public List<String> conflictPairs;
        public String resolution;
    }

    private static class BatchPlanAiAssignment {
        public Long questionId;
        public String questionTitle;
        public String questionIntentTag;
        public String intentRationale;
        public List<String> primaryExperiences;
        public List<String> experienceFacets;
        public String domainBridge;
        public String angle;
        public List<String> focusDetails;
        public List<String> learningPoints;
        public List<String> avoidDetails;
        public String reasoning;
    }
}
