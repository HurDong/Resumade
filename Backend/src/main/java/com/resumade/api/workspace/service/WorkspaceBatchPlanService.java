package com.resumade.api.workspace.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumade.api.experience.domain.Experience;
import com.resumade.api.experience.domain.ExperienceFacet;
import com.resumade.api.experience.domain.ExperienceRepository;
import com.resumade.api.workspace.domain.Application;
import com.resumade.api.workspace.domain.ApplicationRepository;
import com.resumade.api.workspace.domain.CompanyFitProfile;
import com.resumade.api.workspace.domain.CompanyFitProfileRepository;
import com.resumade.api.workspace.dto.BatchPlanRequest;
import com.resumade.api.workspace.dto.BatchPlanResponse;
import com.resumade.api.workspace.prompt.QuestionCategory;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static final int TOKENS_PER_QUESTION      = 1200;
    private static final int MIN_BATCH_PLAN_TOKENS     = 3600;
    private static final int MAX_BATCH_PLAN_TOKENS     = 12000;
    private static final double RETRY_TOKEN_MULTIPLIER = 1.6;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{IsAlphabetic}\\p{IsDigit}\\u3131-\\u318E\\uAC00-\\uD7A3+#._-]+");
    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "for", "with", "from", "that", "this", "your", "into", "self", "intro", "introduction",
            "resume", "question", "company", "position", "role", "experience", "project", "draft", "paragraph",
            "focus", "tone", "keep", "avoid", "more", "detail", "details", "specific", "specificity", "structure",
            "지원", "문항", "자소서", "자기소개서", "질문", "회사", "직무", "경험", "프로젝트", "내용", "작성"
    );
    private static final Set<String> AI_DOMAIN_TOKENS = Set.of(
            "ai", "ml", "llm", "rag", "nlp", "cv", "vision", "inference", "finetuning", "prompt",
            "embedding", "vector", "retrieval", "model", "training", "eval", "evaluation",
            "머신러닝", "딥러닝", "생성형", "모델", "추론", "학습", "파인튜닝", "임베딩", "벡터", "검색"
    );
    private static final String SYSTEM_PROMPT = """
            You are a senior cover-letter strategy architect for Korean self-introduction questions.
            Your task: design a differentiated, interview-verifiable writing strategy for EVERY question in one batch.
            Work through all four reasoning steps below IN ORDER before writing any assignment.
            Return valid JSON only. Follow the schema exactly.

            <Step1_IntentClassification>
            Before assigning any experience, classify EACH question's core evaluation intent.
            Use EXACTLY ONE tag from this shared taxonomy per question:

              MOTIVATION       – support motive, domain interest, company/role choice, join goal, near-term contribution
              EXPERIENCE       – technical experience, project execution, engineering judgment, measurable outcome
              PROBLEM_SOLVING  – root-cause analysis, challenge, troubleshooting, adaptation under constraint
              COLLABORATION    – teamwork, shared goal, role split, coordination, conflict resolution, communication
              PERSONAL_GROWTH   – formative episode, turning point, value formation, and how it explains the applicant's current work principle (NOT technical growth)
              CULTURE_FIT      – fast execution, MVP judgment, customer focus, experiment culture, ownership
              TREND_INSIGHT    – industry/technology issue analysis, business implication, company relevance
              DEFAULT          – only when none of the above clearly dominates

            Rules:
            - If a question title is about 지원동기, 입사 후 포부, 회사/직무 선택 이유 → MOTIVATION.
            - If it asks about 협업, 공동 목표, 갈등, 조율, 팀 프로젝트, 팀 성과와 개인 기여 → COLLABORATION.
            - If it asks about 성장과정, 가치관, 전환점, 나를 만든 경험, 또는 과거 경험이 현재의 행동 원칙으로 이어진 흐름 → PERSONAL_GROWTH (NOT about tech learning or company-choice motive).
            - If it asks about 빠른 실행, 고객 반응, 실험, 적응, 새로운 방식 활용 → prefer CULTURE_FIT or PROBLEM_SOLVING depending on whether the emphasis is working style or diagnosis.
            - If it asks about 최근 기술/산업/사회 이슈 견해 → TREND_INSIGHT.
            - DEFAULT should be rare.
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
            If the question's intentTag is MOTIVATION, the facet must point to a moment
            where you realized alignment between your work and this company's business domain.
            </Step2_FacetMapping>

            <Step3_DomainBridge>
            For EVERY assignment, write a domainBridge: a 1–2 sentence logical bridge that connects
            your technical achievement to this specific company's business value.

            Format: "[technical outcome] → [why it matters for THIS company's domain]"

            Examples:
              "검색 응답속도 95ms 단축 → 식품안전정보원의 국민 데이터 접근성과 신뢰도 직결"
              "결제 API 타임아웃 방어 로직 → 금융 서비스 SLA 달성과 고객 신뢰 유지에 직결"

            If the question is COLLABORATION, the bridge must connect the coordination role and outcome
            to team delivery quality, handoff efficiency, or operational impact — NOT individual technical achievement.
            Format: "[coordination role or process] → [team delivery or operational impact at THIS company]"

            If the question is PROBLEM_SOLVING, the bridge must connect the diagnosis and judgment process
            to the role's problem-solving capability — NOT just the final result metric.
            Format: "[root cause diagnosed + solution chosen] → [why that judgment matters for THIS company's role]"

            If the question is CULTURE_FIT, the bridge must connect a concrete behavior episode
            to how that working style fits the company's culture or values — NOT a generic competency statement.
            Format: "[specific behavior in context] → [why it fits THIS company's working culture or value]"

            EXCEPTION - If the question's intentTag is TREND_INSIGHT:
            The domainBridge must NOT start from a personal technical achievement.
            Instead, connect one external industry or technology trend to a concrete company-side application or impact.
            Format: "[external trend or technology] → [concrete application or impact at THIS company's domain]"
            Example: "근거 기반 생성형 AI 확산 → 제주은행 내부 약관/상품 문서 검색 고도화와 고객 응대 품질 향상에 직결"
            The applicant's experience may be referenced only as supporting evidence, never as the starting point.
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
    private final CompanyFitProfileRepository companyFitProfileRepository;
    private final ExperienceRepository experienceRepository;
    private final ObjectMapper objectMapper;
    private final QuestionClassifierService questionClassifierService;

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

        List<Experience> experiences = experienceRepository.findAllWithFacets();
        if (apiKey == null || apiKey.isBlank() || "demo".equalsIgnoreCase(apiKey.trim())) {
            log.warn("OpenAI API key missing for workspace batch plan. Falling back to heuristic planning.");
            return buildHeuristicPlan(application, questions, experiences);
        }

        int initialTokens = resolveTokenBudget(questions.size(), 1.0);
        int retryTokens   = resolveTokenBudget(questions.size(), RETRY_TOKEN_MULTIPLIER);
        log.info("Workspace batch plan token budget questionCount={} initialTokens={} retryTokens={}",
                questions.size(), initialTokens, retryTokens);

        try {
            JsonNode response = requestPlanFromOpenAi(application, questions, experiences, initialTokens);
            BatchPlanAiResponse parsed = parsePlanResponse(response);
            return toResponse(parsed, questions, application, experiences);
        } catch (Exception e) {
            log.warn("Workspace batch planning primary attempt failed. Retrying with larger budget. model={} initialTokens={} retryTokens={}",
                    modelName, initialTokens, retryTokens, e);
            try {
                JsonNode retryResponse = requestPlanFromOpenAi(application, questions, experiences, retryTokens);
                BatchPlanAiResponse parsed = parsePlanResponse(retryResponse);
                return toResponse(parsed, questions, application, experiences);
            } catch (Exception retryException) {
                log.warn("Workspace batch planning failed. Falling back to heuristic plan. model={}", modelName, retryException);
                return buildHeuristicPlan(application, questions, experiences);
            }
        }
    }

    private int resolveTokenBudget(int questionCount, double multiplier) {
        int base = (int) Math.ceil(questionCount * TOKENS_PER_QUESTION * multiplier);
        return Math.min(MAX_BATCH_PLAN_TOKENS, Math.max(MIN_BATCH_PLAN_TOKENS, base));
    }

    private JsonNode requestPlanFromOpenAi(
            Application application,
            List<BatchPlanRequest.QuestionSnapshot> questions,
            List<Experience> experiences,
            int maxOutputTokens
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
        requestBody.put("max_output_tokens", maxOutputTokens);
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
        String fitProfileBlock = buildFitProfileBlock(application);

        return """
                Company: %s
                Position: %s

                Company fit context:
                - AI insight: %s
                - Company research: %s
                - Raw JD: %s

                Active company Fit profile:
                %s

                Questions:
                %s

                Experience vault:
                %s

                Planning rules:
                - userDirective always takes the highest priority. Read each question's userDirective first and honor it exactly before applying any default rule.
                - If an active company Fit profile exists, use it as the strongest company-specific anchor. Do not invent company facts outside the Fit profile, JD, research, or supplied evidence.
                - Ignore any previous internal batch strategy or previously generated draft wording. Re-plan from the current experience vault and current company context.
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
                fitProfileBlock,
                questionsBlock,
                experiencesBlock
        );
    }

    private String buildFitProfileBlock(Application application) {
        if (application == null || application.getId() == null) {
            return "No active company Fit profile.";
        }
        return companyFitProfileRepository.findByApplicationId(application.getId())
                .map(this::formatFitProfileBlock)
                .orElse("No active company Fit profile.");
    }

    private String formatFitProfileBlock(CompanyFitProfile profile) {
        return """
                groundingStatus: %s
                reviewNote: %s
                profileJson: %s
                """.formatted(
                safe(profile.getGroundingStatus()),
                safe(snippet(profile.getReviewNote(), 600)),
                safe(snippet(profile.getProfileJson(), 1600))
        );
    }

    private String formatQuestionBlock(BatchPlanRequest.QuestionSnapshot question) {
        QuestionCategory category = resolveQuestionCategory(question);
        return """
                [QUESTION]
                questionId: %d
                title: %s
                shared category hint: %s
                maxLength: %s
                current user directive: %s
                """.formatted(
                question.getQuestionId(),
                safe(question.getTitle()),
                category.name() + " (" + category.getDisplayName() + ")",
                question.getMaxLength() == null ? "unknown" : question.getMaxLength(),
                safe(snippet(question.getUserDirective(), 320))
        );
    }

    private String formatExperienceBlock(Experience experience) {
        return """
                [EXPERIENCE]
                title: %s
                organization: %s
                origin: %s
                role: %s
                period: %s
                category: %s
                tech stack: %s
                overall tech stack: %s
                metrics: %s
                project keywords: %s
                project question types: %s
                summary: %s
                facets:
                %s
                raw details: %s
                """.formatted(
                safe(experience.getTitle()),
                safe(experience.getOrganization()),
                safe(experience.getOrigin()),
                safe(experience.getRole()),
                safe(experience.getPeriod()),
                safe(experience.getCategory()),
                safe(joinJsonArray(experience.getTechStack())),
                safe(joinJsonArray(experience.getOverallTechStack())),
                safe(joinJsonArray(experience.getMetrics())),
                safe(joinJsonArray(experience.getJobKeywords())),
                safe(joinJsonArray(experience.getQuestionTypes())),
                safe(snippet(experience.getDescription(), MAX_EXPERIENCE_DESCRIPTION_CHARS)),
                safe(formatFacetBlocks(experience)),
                safe(snippet(experience.getRawContent(), MAX_EXPERIENCE_RAW_CHARS))
        );
    }

    private String formatFacetBlocks(Experience experience) {
        if (experience == null || experience.getFacets() == null || experience.getFacets().isEmpty()) {
            return "- none";
        }

        return experience.getFacets().stream()
                .map(this::formatFacetBlock)
                .collect(Collectors.joining("\n"));
    }

    private String formatFacetBlock(ExperienceFacet facet) {
        return """
                - facet title: %s
                  situation: %s
                  role: %s
                  judgment: %s
                  actions: %s
                  results: %s
                  tech stack: %s
                  job keywords: %s
                  question types: %s
                """.formatted(
                safe(facet.getTitle()),
                safe(joinJsonArray(facet.getSituation())),
                safe(joinJsonArray(facet.getRole())),
                safe(joinJsonArray(facet.getJudgment())),
                safe(joinJsonArray(facet.getActions())),
                safe(joinJsonArray(facet.getResults())),
                safe(joinJsonArray(facet.getTechStack())),
                safe(joinJsonArray(facet.getJobKeywords())),
                safe(joinJsonArray(facet.getQuestionTypes()))
        ).trim();
    }

    private BatchPlanAiResponse parsePlanResponse(JsonNode response) throws IOException {
        JsonNode structuredNode = extractStructuredOutputNode(response);
        if (structuredNode != null && !structuredNode.isMissingNode() && !structuredNode.isNull()) {
            return objectMapper.treeToValue(structuredNode, BatchPlanAiResponse.class);
        }

        String outputText = extractOutputText(response).trim();
        if (outputText.isBlank()) {
            throw new IllegalStateException("Responses API batch plan payload was empty");
        }

        try {
            return objectMapper.readValue(outputText, BatchPlanAiResponse.class);
        } catch (IOException primary) {
            String recovered = extractFirstJsonObject(outputText);
            if (!recovered.equals(outputText)) {
                return objectMapper.readValue(recovered, BatchPlanAiResponse.class);
            }
            log.warn("Failed to parse batch plan output as JSON. snippet={}", snippet(outputText, 600), primary);
            throw primary;
        }
    }

    private BatchPlanResponse toResponse(
            BatchPlanAiResponse parsed,
            List<BatchPlanRequest.QuestionSnapshot> questions,
            Application application,
            List<Experience> experiences
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
                    .category(resolveQuestionCategory(question))
                    .build());
        }

        if (assignments.isEmpty()) {
            return buildHeuristicPlan(
                    application,
                    questions,
                    experiences == null || experiences.isEmpty() ? experienceRepository.findAllWithFacets() : experiences
            );
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

            QuestionCategory category = resolveQuestionCategory(question);
            String intentTag = formatIntentTag(category);
            List<String> focusDetails = inferFallbackFocusDetails(category, question.getTitle(), experiences, index);
            List<String> learningPoints = inferFallbackLearningPoints(category);
            List<String> avoidDetails = List.of(
                    "다른 문항과 동일한 기술 결정 설명 반복 금지",
                    "다른 문항과 동일한 배운점 문장 반복 금지"
            );
            String angle = inferFallbackAngle(category);

            BatchPlanAiAssignment stub = new BatchPlanAiAssignment();
            stub.questionIntentTag = intentTag;
            stub.intentRationale = "Heuristic fallback: aligned to shared question category taxonomy.";
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
                    .intentRationale("Heuristic fallback: aligned to shared question category taxonomy.")
                    .primaryExperiences(chosenExperiences)
                    .experienceFacets(List.of())
                    .domainBridge("")
                    .angle(angle)
                    .focusDetails(focusDetails)
                    .learningPoints(learningPoints)
                    .avoidDetails(avoidDetails)
                    .reasoning("Fallback heuristic plan generated because structured planning was unavailable.")
                    .directivePrefix(buildDirectivePrefix(stub))
                    .category(category)
                    .build());
        }

        return BatchPlanResponse.builder()
                .coverageSummary("공유 카테고리 체계를 기준으로 문항 의도를 먼저 맞춘 뒤, 세부 기술과 배운점이 겹치지 않도록 기본 분산 전략을 적용했습니다.")
                .globalGuardrails(List.of(
                        "같은 프로젝트 재사용은 허용하되 세부 기술 포인트는 분리",
                        "동일한 배운점과 결과 서술 반복 금지",
                        "문항별 첫 문장 주장과 증거 축 분리",
                        "배치 미리보기와 실제 초안 생성이 같은 카테고리 체계를 사용하도록 정렬"
                ))
                .overlapValidation(BatchPlanResponse.OverlapValidation.builder()
                        .isClean(true).conflictPairs(List.of()).resolution("Heuristic fallback — no validation performed.").build())
                .model(modelName + " (heuristic fallback)")
                .assignments(assignments)
                .build();
    }

    private BatchPlanResponse buildHeuristicPlan(
            Application application,
            List<BatchPlanRequest.QuestionSnapshot> questions,
            List<Experience> experiences
    ) {
        Set<Long> alreadyAssignedExperienceIds = new LinkedHashSet<>();
        List<BatchPlanResponse.Assignment> assignments = new ArrayList<>();

        for (int index = 0; index < questions.size(); index++) {
            BatchPlanRequest.QuestionSnapshot question = questions.get(index);
            QuestionCategory category = resolveQuestionCategory(question);
            HeuristicSelection selection = selectHeuristicExperience(
                    application,
                    question,
                    experiences,
                    category,
                    alreadyAssignedExperienceIds
            );

            if (selection.experienceId() != null) {
                alreadyAssignedExperienceIds.add(selection.experienceId());
            }

            List<String> chosenExperiences = selection.experienceTitle() == null
                    ? List.of("관련 경험 선택 필요")
                    : List.of(selection.experienceTitle());
            String intentTag = formatIntentTag(category);
            List<String> focusDetails = selection.focusDetails().isEmpty()
                    ? inferFallbackFocusDetails(category, question.getTitle(), experiences, index)
                    : selection.focusDetails();
            List<String> learningPoints = inferFallbackLearningPoints(category);
            List<String> avoidDetails = List.of(
                    "다른 문항과 동일한 기술 결정 설명 반복 금지",
                    "다른 문항과 동일한 배운점 문장 반복 금지"
            );
            String angle = inferFallbackAngle(category);

            BatchPlanAiAssignment stub = new BatchPlanAiAssignment();
            stub.questionIntentTag = intentTag;
            stub.intentRationale = "Heuristic fallback: aligned to shared question category taxonomy.";
            stub.primaryExperiences = chosenExperiences;
            stub.experienceFacets = selection.facetTitle() == null ? List.of() : List.of(selection.facetTitle());
            stub.domainBridge = selection.domainBridge();
            stub.angle = angle;
            stub.focusDetails = focusDetails;
            stub.learningPoints = learningPoints;
            stub.avoidDetails = avoidDetails;

            assignments.add(BatchPlanResponse.Assignment.builder()
                    .questionId(question.getQuestionId())
                    .questionTitle(question.getTitle())
                    .questionIntentTag(intentTag)
                    .intentRationale("Heuristic fallback: aligned to shared question category taxonomy.")
                    .primaryExperiences(chosenExperiences)
                    .experienceFacets(selection.facetTitle() == null ? List.of() : List.of(selection.facetTitle()))
                    .domainBridge(selection.domainBridge())
                    .angle(angle)
                    .focusDetails(focusDetails)
                    .learningPoints(learningPoints)
                    .avoidDetails(avoidDetails)
                    .reasoning("Fallback heuristic plan generated because structured planning was unavailable.")
                    .directivePrefix(buildDirectivePrefix(stub))
                    .category(category)
                    .build());
        }

        return BatchPlanResponse.builder()
                .coverageSummary("공유 카테고리 체계를 기준으로 문항 의도를 먼저 맞춘 뒤, 지원 직무와 공고 문맥에 더 가까운 경험을 우선 배치하도록 fallback 전략을 보정했습니다.")
                .globalGuardrails(List.of(
                        "같은 프로젝트 서사는 허용되되 핵심 기술 사인은 분리",
                        "동일한 배운점과 결과 수치 반복 금지",
                        "문항별 첫 문장 주장과 증거 축 분리",
                        "배치 미리보기와 실제 초안 생성에 같은 카테고리 체계를 사용하도록 정렬"
                ))
                .overlapValidation(BatchPlanResponse.OverlapValidation.builder()
                        .isClean(true).conflictPairs(List.of()).resolution("Heuristic fallback - no validation performed.").build())
                .model(modelName + " (heuristic fallback)")
                .assignments(assignments)
                .build();
    }

    private HeuristicSelection selectHeuristicExperience(
            Application application,
            BatchPlanRequest.QuestionSnapshot question,
            List<Experience> experiences,
            QuestionCategory category,
            Set<Long> alreadyAssignedExperienceIds
    ) {
        if (experiences == null || experiences.isEmpty()) {
            return HeuristicSelection.empty();
        }

        Set<String> queryTokens = tokenize(buildHeuristicQuery(application, question, category));
        if (queryTokens.isEmpty()) {
            Experience first = experiences.get(0);
            return HeuristicSelection.fromExperience(first, selectRepresentativeFacet(first), List.of(), "");
        }

        return experiences.stream()
                .map(experience -> scoreHeuristicExperience(
                        application,
                        question,
                        experience,
                        category,
                        queryTokens,
                        alreadyAssignedExperienceIds
                ))
                .max(Comparator
                        .comparingDouble(HeuristicSelection::score)
                        .thenComparing(selection -> safe(selection.experienceTitle())))
                .orElse(HeuristicSelection.empty());
    }

    private HeuristicSelection scoreHeuristicExperience(
            Application application,
            BatchPlanRequest.QuestionSnapshot question,
            Experience experience,
            QuestionCategory category,
            Set<String> queryTokens,
            Set<Long> alreadyAssignedExperienceIds
    ) {
        double titleWeight = 18;
        double descriptionWeight = 18;
        double techWeight = 16;
        double keywordWeight = 28;
        double resultWeight = 10;
        double roleWeight = 8;

        switch (category != null ? category : QuestionCategory.DEFAULT) {
            case MOTIVATION -> {
                keywordWeight = 34;
                descriptionWeight = 20;
                techWeight = 10;
                roleWeight = 10;
                resultWeight = 6;
            }
            case EXPERIENCE -> {
                techWeight = 28;
                resultWeight = 18;
                descriptionWeight = 22;
                keywordWeight = 14;
            }
            case PROBLEM_SOLVING -> {
                descriptionWeight = 28;
                resultWeight = 16;
                techWeight = 14;
            }
            case COLLABORATION -> {
                descriptionWeight = 24;
                roleWeight = 18;
                keywordWeight = 18;
            }
            case PERSONAL_GROWTH -> {
                descriptionWeight = 22;
                keywordWeight = 24;
                techWeight = 4; // 기술 스택 관련성 낮음
            }
            case TREND_INSIGHT -> {
                keywordWeight = 26;
                descriptionWeight = 20;
                techWeight = 18;
            }
            default -> {
            }
        }

        Set<String> titleTokens = tokenize(joinNonBlank(
                safe(experience.getTitle()),
                experience.getFacets().stream()
                        .map(ExperienceFacet::getTitle)
                        .filter(Objects::nonNull)
                        .collect(Collectors.joining(" "))
        ));
        Set<String> descriptionTokens = tokenize(joinNonBlank(
                safe(experience.getDescription()),
                safe(experience.getOrigin()),
                safe(experience.getOrganization()),
                facetText(experience, ExperienceFacet::getSituation),
                facetText(experience, ExperienceFacet::getJudgment),
                facetText(experience, ExperienceFacet::getActions)
        ));
        Set<String> techTokens = tokenize(joinNonBlank(
                joinJsonArray(experience.getTechStack()),
                joinJsonArray(experience.getOverallTechStack()),
                facetText(experience, ExperienceFacet::getTechStack)
        ));
        Set<String> keywordTokens = tokenize(joinNonBlank(
                joinJsonArray(experience.getJobKeywords()),
                joinJsonArray(experience.getQuestionTypes()),
                facetText(experience, ExperienceFacet::getJobKeywords),
                facetText(experience, ExperienceFacet::getQuestionTypes)
        ));
        Set<String> resultTokens = tokenize(joinNonBlank(
                joinJsonArray(experience.getMetrics()),
                facetText(experience, ExperienceFacet::getResults)
        ));
        Set<String> roleTokens = tokenize(joinNonBlank(
                safe(experience.getRole()),
                facetText(experience, ExperienceFacet::getRole)
        ));

        double score = 0;
        score += overlapScore(queryTokens, titleTokens, titleWeight);
        score += overlapScore(queryTokens, descriptionTokens, descriptionWeight);
        score += overlapScore(queryTokens, techTokens, techWeight);
        score += overlapScore(queryTokens, keywordTokens, keywordWeight);
        score += overlapScore(queryTokens, resultTokens, resultWeight);
        score += overlapScore(queryTokens, roleTokens, roleWeight);

        boolean queryHasAiSignal = containsAnyToken(queryTokens, AI_DOMAIN_TOKENS);
        Set<String> experienceTokens = new LinkedHashSet<>();
        experienceTokens.addAll(titleTokens);
        experienceTokens.addAll(descriptionTokens);
        experienceTokens.addAll(techTokens);
        experienceTokens.addAll(keywordTokens);

        if (queryHasAiSignal && containsAnyToken(experienceTokens, AI_DOMAIN_TOKENS)) {
            score += 18;
        }

        if (alreadyAssignedExperienceIds != null
                && experience.getId() != null
                && alreadyAssignedExperienceIds.contains(experience.getId())) {
            score -= 6;
        }

        ExperienceFacet representativeFacet = selectRepresentativeFacet(experience);
        List<String> focusDetails = buildHeuristicFocusDetails(experience, representativeFacet);
        String domainBridge = buildHeuristicDomainBridge(application, question, experience, representativeFacet, category);

        return new HeuristicSelection(
                experience.getId(),
                safe(experience.getTitle()),
                representativeFacet == null ? null : safe(representativeFacet.getTitle()),
                normalizeList(focusDetails),
                safe(domainBridge),
                score
        );
    }

    private String buildHeuristicQuery(
            Application application,
            BatchPlanRequest.QuestionSnapshot question,
            QuestionCategory category
    ) {
        return joinNonBlank(
                question == null ? "" : safe(question.getTitle()),
                application == null ? "" : safe(application.getPosition()),
                application == null ? "" : snippet(application.getAiInsight(), 220),
                application == null ? "" : snippet(application.getCompanyResearch(), 220),
                application == null ? "" : snippet(application.getRawJd(), 220),
                question == null ? "" : safe(question.getUserDirective()),
                category == null ? "" : category.name()
        );
    }

    private ExperienceFacet selectRepresentativeFacet(Experience experience) {
        if (experience == null || experience.getFacets() == null || experience.getFacets().isEmpty()) {
            return null;
        }

        return experience.getFacets().stream()
                .max(Comparator.comparingInt(facet -> tokenize(joinNonBlank(
                        joinJsonArray(facet.getSituation()),
                        joinJsonArray(facet.getJudgment()),
                        joinJsonArray(facet.getActions()),
                        joinJsonArray(facet.getResults()),
                        joinJsonArray(facet.getTechStack())
                )).size()))
                .orElse(experience.getFacets().get(0));
    }

    private List<String> buildHeuristicFocusDetails(Experience experience, ExperienceFacet facet) {
        List<String> details = new ArrayList<>();
        if (facet != null) {
            details.addAll(readJsonArray(facet.getTechStack()));
            details.addAll(readJsonArray(facet.getResults()));
            details.addAll(readJsonArray(facet.getJudgment()));
        }
        details.addAll(readJsonArray(experience.getJobKeywords()));
        details.addAll(readJsonArray(experience.getQuestionTypes()));
        if (experience.getDescription() != null && !experience.getDescription().isBlank()) {
            details.add(snippet(experience.getDescription(), 90));
        }
        return normalizeList(details);
    }

    private String buildHeuristicDomainBridge(
            Application application,
            BatchPlanRequest.QuestionSnapshot question,
            Experience experience,
            ExperienceFacet facet,
            QuestionCategory category
    ) {
        String company = application == null ? "" : safe(application.getCompanyName());
        String position = application == null ? "" : safe(application.getPosition());
        String result = facet != null
                ? readJsonArray(facet.getResults()).stream().findFirst().orElse("")
                : readJsonArray(experience.getMetrics()).stream().findFirst().orElse("");
        String detail = facet != null
                ? safe(facet.getTitle())
                : safe(experience.getTitle());

        if (category == QuestionCategory.MOTIVATION) {
            return firstNonBlank(
                    joinNonBlank(
                            detail,
                            "경험이",
                            company.isBlank() ? position : company + "의 " + position,
                            "문제를 더 직접적으로 다루고 싶다는 동기로 이어졌습니다."
                    ),
                    ""
            );
        }

        if (category == QuestionCategory.TREND_INSIGHT) {
            // Trend-first bridge: connect an external trend to the company domain.
            // The applicant's experience is not the starting point here.
            String companyDomain = company.isBlank() ? position : company + "의 " + position;
            return firstNonBlank(
                    joinNonBlank(
                            "최신 기술/산업 트렌드를",
                            companyDomain,
                            "맥락에서 해석하고, 실제 서비스나 시스템에 적용할 수 있는 방향을 제시합니다."
                    ),
                    ""
            );
        }

        if (category == QuestionCategory.COLLABORATION) {
            // Collaboration bridge: connect team coordination outcome to company delivery or operational impact.
            // Do NOT anchor on individual technical achievement.
            return firstNonBlank(
                    joinNonBlank(
                            detail,
                            "경험에서 맡은 역할과 조율 방식이",
                            company.isBlank() ? position : company + "의 " + position,
                            "팀 납기 품질과 운영 안정성에 기여할 수 있는 협업 역량으로 이어집니다."
                    ),
                    ""
            );
        }

        if (category == QuestionCategory.CULTURE_FIT) {
            // Culture-fit bridge: connect working style or trait evidence to company culture fit.
            return firstNonBlank(
                    joinNonBlank(
                            detail,
                            "경험에서 보여준 일하는 방식과 판단 기준이",
                            company.isBlank() ? position : company + "의 " + position,
                            "조직 문화와 맞닿아 있습니다."
                    ),
                    ""
            );
        }

        if (category == QuestionCategory.PROBLEM_SOLVING) {
            // Problem-solving bridge: connect diagnosis and judgment to role-level capability signal.
            return firstNonBlank(
                    joinNonBlank(
                            result.isBlank() ? detail : result,
                            "과정에서 드러난 원인 진단과 해결 판단이",
                            company.isBlank() ? position : company + "의 " + position,
                            "직무에서 문제를 구조적으로 다루는 역량으로 연결됩니다."
                    ),
                    ""
            );
        }

        return firstNonBlank(
                joinNonBlank(
                        result.isBlank() ? detail : result,
                        "성과를",
                        company.isBlank() ? position : company + "의 " + position,
                        "문맥에서 활용 가능한 역량으로 연결합니다."
                ),
                ""
        );
    }

    private String facetText(Experience experience, java.util.function.Function<ExperienceFacet, String> extractor) {
        if (experience == null || experience.getFacets() == null || experience.getFacets().isEmpty()) {
            return "";
        }

        return experience.getFacets().stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" "));
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptySet();
        }

        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        Matcher matcher = TOKEN_PATTERN.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String token = matcher.group();
            if (token.length() < 2 || STOPWORDS.contains(token)) {
                continue;
            }
            tokens.add(token);
        }
        return tokens;
    }

    private double overlapScore(Set<String> queryTokens, Set<String> targetTokens, double weight) {
        if (queryTokens == null || queryTokens.isEmpty() || targetTokens == null || targetTokens.isEmpty()) {
            return 0;
        }

        long matches = queryTokens.stream()
                .filter(targetTokens::contains)
                .count();
        if (matches == 0) {
            return 0;
        }

        double coverage = (double) matches / queryTokens.size();
        double density = (double) matches / targetTokens.size();
        return weight * Math.min(1.0, coverage + (density * 0.35));
    }

    private boolean containsAnyToken(Set<String> sourceTokens, Set<String> expectedTokens) {
        if (sourceTokens == null || sourceTokens.isEmpty() || expectedTokens == null || expectedTokens.isEmpty()) {
            return false;
        }
        return sourceTokens.stream().anyMatch(expectedTokens::contains);
    }

    private String joinNonBlank(String... values) {
        return Arrays.stream(values)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(" "));
    }

    private QuestionCategory resolveQuestionCategory(BatchPlanRequest.QuestionSnapshot question) {
        if (question == null || question.getTitle() == null || question.getTitle().isBlank()) {
            return QuestionCategory.DEFAULT;
        }
        // 유저가 직접 지정한 카테고리가 있으면 AI 분류 없이 즉시 반환
        if (question.getCategory() != null) {
            return question.getCategory();
        }
        return questionClassifierService.classify(question.getTitle());
    }

    private String formatIntentTag(QuestionCategory category) {
        QuestionCategory effective = category != null ? category : QuestionCategory.DEFAULT;
        return effective.name() + " | " + effective.getDisplayName();
    }

    private List<String> inferFallbackFocusDetails(
            QuestionCategory category,
            String questionTitle,
            List<Experience> experiences,
            int index
    ) {
        List<String> candidates = new ArrayList<>();
        if (!experiences.isEmpty()) {
            Experience selected = experiences.get(index % experiences.size());
            candidates.addAll(normalizeList(readJsonArray(selected.getTechStack())));
            if (selected.getDescription() != null && !selected.getDescription().isBlank()) {
                candidates.add(snippet(selected.getDescription(), 80));
            }
        }

        switch (category != null ? category : QuestionCategory.DEFAULT) {
            case MOTIVATION -> candidates.add("지원 기업 도메인과 내 경험이 맞닿은 구체적 접점");
            case EXPERIENCE -> candidates.add("직무에서 즉시 발휘 가능한 기술 판단과 실행 이력");
            case PROBLEM_SOLVING -> candidates.add("문제 원인 규명과 해결 방식의 차이를 보여주는 판단 근거");
            case COLLABORATION -> candidates.add("공동 목표 아래 맡은 역할과 조율 방식");
            case PERSONAL_GROWTH -> candidates.add("대표 경험이 만든 현재의 행동 원칙과 가치 형성의 연결고리");
            case CULTURE_FIT -> candidates.add("빠르게 실행하고 검증한 working style의 증거");
            case TREND_INSIGHT -> candidates.add("회사 도메인과 연결되는 이슈 해석 관점");
            default -> candidates.add("문항 의도에 맞는 핵심 증거 한 축");
        }

        return normalizeList(candidates).stream().limit(3).toList();
    }

    private List<String> inferFallbackLearningPoints(QuestionCategory category) {
        return switch (category != null ? category : QuestionCategory.DEFAULT) {
            case MOTIVATION -> List.of("기술 선택을 사용자 가치와 연결하는 기준");
            case EXPERIENCE -> List.of("기술 역량이 실제 비즈니스 임팩트로 이어진 경로");
            case PROBLEM_SOLVING -> List.of("문제 해결 경험을 직무 역량으로 번역하는 시각");
            case COLLABORATION -> List.of("팀 목표를 위해 역할을 조정하고 기준을 맞추는 능력");
            case PERSONAL_GROWTH -> List.of("형성된 가치가 지금의 판단 기준과 일하는 태도에 미치는 영향");
            case CULTURE_FIT -> List.of("빠르게 만들고 실제 신호로 검증하는 일하는 방식");
            case TREND_INSIGHT -> List.of("기술 이슈를 회사 맥락으로 해석하는 관점");
            default -> List.of("문항 의도에 맞는 증거를 선별하는 기준");
        };
    }

    private String inferFallbackAngle(QuestionCategory category) {
        return switch (category != null ? category : QuestionCategory.DEFAULT) {
            case MOTIVATION -> "이 기업의 도메인과 내 경험이 맞닿은 접점에서 출발하는 지원동기 각도";
            case EXPERIENCE -> "보유 역량과 실행 이력을 직무 수행 청사진으로 연결하는 역량 어필 각도";
            case PROBLEM_SOLVING -> "핵심 문제를 해결하며 만든 판단과 실행의 차별점을 보여주는 각도";
            case COLLABORATION -> "공동 목표 아래 역할 분담과 조율 역량을 증명하는 각도";
            case PERSONAL_GROWTH -> "한 경험이 만든 가치가 현재의 행동 원칙으로 이어지는 서사 각도";
            case CULTURE_FIT -> "빠르게 실행하고 검증한 방식이 조직 문화와 맞닿는 각도";
            case TREND_INSIGHT -> "기술·산업 이슈를 회사의 현재 사업 맥락과 연결해 해석하는 각도";
            default -> "문항 의도에 맞는 핵심 증거를 한 축으로 세우는 각도";
        };
    }

    private String buildDirectivePrefix(BatchPlanAiAssignment a) {
        List<String> lines = new ArrayList<>();
        lines.add("[Batch Strategy]");
        lines.add("Use with any user directive. User directive takes higher priority.");

        String intentTag = safe(a.questionIntentTag);
        if (!"None".equals(intentTag)) {
            lines.add("Question intent: " + intentTag);
            if (intentTag.contains("MOTIVATION")) {
                lines.add("→ Start from domain/company interest. Do NOT open with tech stack.");
            } else if (intentTag.contains("PROBLEM_SOLVING")) {
                lines.add("→ Lead with the root cause diagnosis, not the surface symptom. Make the judgment criteria visible: why this solution over the alternatives, given real constraints. Impact of inaction must be stated. Reflection must be specific — what concretely changed in thinking or behavior, not 'I learned the importance of X'.");
            } else if (intentTag.contains("COLLABORATION")) {
                lines.add("→ Lead with the shared goal, your owned role, and the coordination process. Distinguish team outcome from your own contribution.");
            } else if (intentTag.contains("PERSONAL_GROWTH")) {
                lines.add("→ Focus on one formative episode, the value it formed, and how it appears in current behavior. Avoid generic family chronicles or motivation-style company-choice logic.");
            } else if (intentTag.contains("CULTURE_FIT")) {
                lines.add("→ If the question is about working style or company value fit (Sub-type A): prove it with one concrete execution-and-validation episode. If the question is about personality strengths/weaknesses (Sub-type B): name the specific trait, show how it played out in a real team project, and for weaknesses show the before/after behavioral change. Do NOT declare the trait without project evidence.");
            } else if (intentTag.contains("TREND_INSIGHT")) {
                lines.add("→ Select one external industry or technology trend first. The answer must open with the trend, not with a personal project. The experience listed in 'Supporting evidence' is supporting evidence only — do NOT make it the main topic or the opening of the answer.");
            }
        }

        if (intentTag.contains("TREND_INSIGHT")) {
            // TREND_INSIGHT: trend-first structure.
            // Experience fields are reframed as optional supporting evidence, not the main topic anchor.
            String domainBridge = safe(a.domainBridge);
            if (!"None".equals(domainBridge)) {
                lines.add("Trend-to-company connection (anchor the external trend argument here, NOT a project summary): " + domainBridge);
            }

            String angle = safe(a.angle);
            if (!"None".equals(angle)) {
                lines.add("Angle: " + angle);
            }

            List<String> primaryExperiences = normalizeList(a.primaryExperiences);
            if (!primaryExperiences.isEmpty()) {
                lines.add("Supporting evidence available (use only to strengthen the trend argument — do NOT open with this or make it the main topic): "
                        + String.join(", ", primaryExperiences));
            }

            List<String> experienceFacets = normalizeList(a.experienceFacets);
            if (!experienceFacets.isEmpty()) {
                lines.add("Evidence facet available if needed (supporting role only): "
                        + String.join(" | ", experienceFacets));
            }

            // focusDetails and learningPoints are experience-centric — skip for TREND_INSIGHT
            // to avoid anchoring the answer on the project rather than the external trend.

            List<String> avoidDetails = normalizeList(a.avoidDetails);
            if (!avoidDetails.isEmpty()) {
                lines.add("Avoid reusing from other questions: " + String.join(" | ", avoidDetails));
            }

        } else {
            // Default: experience-centric structure for all other categories.
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

        JsonNode outputs = response.path("output");
        if (outputs.isArray()) {
            for (JsonNode item : outputs) {
                String directItemText = firstNonBlank(
                        extractNodeText(item.get("arguments")),
                        extractNodeText(item.get("json")),
                        extractNodeText(item.get("parsed"))
                );
                if (!directItemText.isBlank()) {
                    return directItemText;
                }

                JsonNode content = item.path("content");
                if (!content.isArray()) {
                    continue;
                }
                for (JsonNode part : content) {
                    String partText = firstNonBlank(
                            extractNodeText(part.get("text")),
                            extractNodeText(part.get("output_text")),
                            extractNodeText(part.get("json")),
                            extractNodeText(part.get("parsed")),
                            extractNodeText(part.get("arguments"))
                    );
                    if (!partText.isBlank()) {
                        return partText;
                    }
                }
            }
        }

        throw new IllegalStateException("Responses API returned no parseable batch plan output");
    }

    private JsonNode extractStructuredOutputNode(JsonNode response) {
        JsonNode[] directCandidates = new JsonNode[] {
                response.get("output_parsed"),
                response.get("parsed"),
                response.get("json")
        };
        for (JsonNode candidate : directCandidates) {
            JsonNode coerced = coerceStructuredNode(candidate);
            if (coerced != null) {
                return coerced;
            }
        }

        JsonNode outputs = response.path("output");
        if (!outputs.isArray()) {
            return null;
        }

        for (JsonNode item : outputs) {
            JsonNode itemCandidate = firstStructuredNode(item.get("parsed"), item.get("json"), item.get("arguments"));
            if (itemCandidate != null) {
                return itemCandidate;
            }

            JsonNode content = item.path("content");
            if (!content.isArray()) {
                continue;
            }

            for (JsonNode part : content) {
                JsonNode partCandidate = firstStructuredNode(
                        part.get("parsed"),
                        part.get("json"),
                        part.get("arguments"),
                        part.get("output_text"),
                        part.get("text")
                );
                if (partCandidate != null) {
                    return partCandidate;
                }
            }
        }

        return null;
    }

    private JsonNode firstStructuredNode(JsonNode... candidates) {
        if (candidates == null) {
            return null;
        }

        for (JsonNode candidate : candidates) {
            JsonNode coerced = coerceStructuredNode(candidate);
            if (coerced != null) {
                return coerced;
            }
        }
        return null;
    }

    private JsonNode coerceStructuredNode(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }

        if (node.isObject() || node.isArray()) {
            return node;
        }

        JsonNode value = node.get("value");
        if (value != null && value != node) {
            JsonNode nested = coerceStructuredNode(value);
            if (nested != null) {
                return nested;
            }
        }

        String extracted = extractNodeText(node).trim();
        if (extracted.isBlank() || (!extracted.startsWith("{") && !extracted.startsWith("["))) {
            return null;
        }

        try {
            return objectMapper.readTree(extracted);
        } catch (Exception ignored) {
            return null;
        }
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

    private String extractFirstJsonObject(String raw) {
        String candidate = raw == null ? "" : raw.trim();
        if (candidate.isBlank()) {
            return "";
        }

        int start = candidate.indexOf('{');
        int end = candidate.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return candidate.substring(start, end + 1);
        }

        int arrayStart = candidate.indexOf('[');
        int arrayEnd = candidate.lastIndexOf(']');
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return candidate.substring(arrayStart, arrayEnd + 1);
        }

        return candidate;
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

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
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

    private record HeuristicSelection(
            Long experienceId,
            String experienceTitle,
            String facetTitle,
            List<String> focusDetails,
            String domainBridge,
            double score
    ) {
        private static HeuristicSelection empty() {
            return new HeuristicSelection(null, null, null, List.of(), "", 0);
        }

        private static HeuristicSelection fromExperience(
                Experience experience,
                ExperienceFacet facet,
                List<String> focusDetails,
                String domainBridge
        ) {
            return new HeuristicSelection(
                    experience == null ? null : experience.getId(),
                    experience == null ? null : experience.getTitle(),
                    facet == null ? null : facet.getTitle(),
                    focusDetails == null ? List.of() : focusDetails,
                    domainBridge == null ? "" : domainBridge,
                    0
            );
        }
    }
}
