package com.resumade.api.workspace.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class OpenAiResponsesWorkspaceDraftService implements WorkspaceDraftAiService {

    private static final String RESPONSES_API_URL = "https://api.openai.com/v1/responses";
    private static final String LENGTH_RETRY_MARKER = "[LENGTH_RETRY]";

    private static final String GENERATE_SYSTEM_PROMPT = """
            You are a senior Korean self-introduction writing assistant.
            Return JSON only with exactly this shape: {"text":"..."}.
            Write in Korean.
            Start with a bracketed title like [Title].
            The first sentence must answer the question directly.
            After the title, write natural prose in 3 to 4 paragraphs when length allows.
            If the hard limit is very small, use fewer paragraphs but keep paragraph breaks natural.
            Keep polished self-introduction narrative style, not report or memo style.
            Do not use explicit section labels or colon-led headers such as Problem:, Action:, Result:, Summary:, 문제:, 원인:, 분석:, 조치:, 결과:, 요약:.
            Do not use numbering, bullets, or list markers such as (1), 1., -, *, first/second.
            Use only supplied facts and context. Do not invent experience.
            When a project is first mentioned, include its origin or provenance if the context provides it.
            Never exceed the hard character limit.
            Count characters the same way Korean resume forms do: spaces, punctuation, brackets, English letters, numbers, and line breaks all count as one visible character.
            Treat the preferred target as an operational goal, not a recommendation.
            Meeting only the minimum is not enough when the preferred target is reachable inside the hard limit.
            If a preferred target window is provided, land inside that window on the first pass whenever possible.
            If the draft is short, expand in this order: background/context -> role/responsibility -> judgment criteria -> execution details -> measurable outcome -> job connection.
            Expand with factual detail, not generic filler.
            Before returning, silently recount characters and revise until the answer stays within the preferred target window and under the hard limit.
            """;

    private static final String REFINE_SYSTEM_PROMPT = """
            You are a senior Korean self-introduction writing assistant.
            Return JSON only with exactly this shape: {"text":"..."}.
            Write in Korean.
            Preserve the strong facts from the current draft while improving structure, specificity, and job fit.
            Keep the bracketed title format.
            The first sentence must answer directly.
            Keep polished self-introduction narrative style, not report or memo style.
            Body paragraphs may be split into 3 to 4 natural paragraphs when length allows.
            Do not use explicit section labels or colon-led headers such as Problem:, Action:, Result:, Summary:, 문제:, 원인:, 분석:, 조치:, 결과:, 요약:.
            Do not use numbering, bullets, or list markers such as (1), 1., -, *, first/second.
            Preserve factual grounding. Do not invent unsupported claims.
            Never exceed the hard character limit.
            Count characters the same way Korean resume forms do: spaces, punctuation, brackets, English letters, numbers, and line breaks all count as one visible character.
            Treat the preferred target as an operational goal, not a recommendation.
            Meeting only the minimum is not enough when the preferred target is reachable inside the hard limit.
            If a preferred target window is provided, land inside that window on the first pass whenever possible.
            If the draft is short, expand in this order: background/context -> role/responsibility -> judgment criteria -> execution details -> measurable outcome -> job connection.
            Expand only weak or missing parts with factual detail; do not add generic filler.
            Before returning, silently recount characters and revise until the answer stays within the preferred target window and under the hard limit.
            """;

    private static final String REFINE_RETRY_SYSTEM_PROMPT = """
            You are a senior Korean self-introduction writing assistant.
            Return JSON only with exactly this shape: {"text":"..."}.
            Write in Korean.
            The previous result failed because it was below the minimum target.
            Keep all strong facts from the current draft and expand only the missing depth.
            Keep the bracketed title format and keep the first sentence directly answering the question.
            Body paragraphs may be split into 3 to 4 natural paragraphs when length allows.
            Keep polished self-introduction narrative style, not report or memo style.
            Do not use explicit section labels or colon-led headers such as Problem:, Action:, Result:, Summary:, 문제:, 원인:, 분석:, 조치:, 결과:, 요약:.
            Do not use numbering, bullets, or list markers such as (1), 1., -, *, first/second.
            Never summarize, compress, or weaken already strong factual sentences.
            Never exceed the hard character limit.
            Count characters the same way Korean resume forms do: spaces, punctuation, brackets, English letters, numbers, and line breaks all count as one visible character.
            Treat the preferred target as an operational goal, not a recommendation.
            Meeting only the minimum is not enough when the preferred target is reachable inside the hard limit.
            Expand in this order: background/context -> role/responsibility -> judgment criteria -> execution details -> measurable outcome -> job connection.
            Use factual detail only and avoid generic filler.
            Before returning, silently recount characters and revise until the answer stays within the preferred target window and under the hard limit.
            """;

    private static final String GENERATE_USER_PROMPT_TEMPLATE = """
            Company: %s
            Position: %s
            Question: %s
            Hard limit: %d characters
            Minimum target (must meet): %d characters
            Preferred target (must aim): %d characters
            Preferred target window: %d to %d characters

            Company context:
            %s

            Experience context:
            %s

            Other questions to avoid overlapping with:
            %s

            User directive:
            %s

            Requirements:
            - Keep [Title] format with a concise, memorable, non-generic headline.
            - The title must not repeat the company name, position name, or question wording.
            - The first sentence must answer the question directly in conclusion-first style.
            - Write the body in 3 to 4 natural paragraphs when length allows.
            - Never use explicit section labels, numbering, bullets, or list formatting.
            - Use one or two strongest experiences, not a broad list.
            - Tie actions and outcomes to the target role.
            - Treat the preferred target as the practical goal for this pass.
            - If short, expand in this order: background -> role -> judgment -> execution detail -> outcome -> job connection.
            - Return JSON only with shape {"text":"..."}.
            """;

    private static final String REFINE_USER_PROMPT_TEMPLATE = """
            Company: %s
            Position: %s
            Hard limit: %d characters
            Minimum target (must meet): %d characters
            Preferred target (must aim): %d characters
            Preferred target window: %d to %d characters

            Company context:
            %s

            Current draft:
            %s

            Experience context:
            %s

            Other questions to avoid overlapping with:
            %s

            User directive:
            %s

            Requirements:
            - Preserve the strongest facts and claims from the current draft.
            - Improve specificity, job fit, and narrative coherence.
            - Keep [Title] format with a concise, memorable, non-generic headline.
            - The first sentence must answer directly.
            - Body paragraphs may be split into 3 to 4 natural paragraphs when length allows.
            - Never use explicit section labels, numbering, bullets, or list formatting.
            - Treat the preferred target as the practical goal for this pass.
            - If short, expand in this order: background -> role -> judgment -> execution detail -> outcome -> job connection.
            - Return JSON only with shape {"text":"..."}.
            """;

    private static final String REFINE_RETRY_USER_PROMPT_TEMPLATE = """
            Company: %s
            Position: %s
            Hard limit: %d characters
            Minimum target (must meet): %d characters
            Preferred target (must aim): %d characters
            Preferred target window: %d to %d characters

            Company context:
            %s

            Current draft:
            %s

            Experience context:
            %s

            Other questions to avoid overlapping with:
            %s

            Retry feedback:
            %s

            Requirements:
            - The previous output was under the minimum target. Fix this in this retry.
            - Preserve all strong facts already present.
            - Expand only missing parts; do not summarize or compress existing strong content.
            - Keep [Title] and direct first sentence.
            - Body paragraphs may be split into 3 to 4 natural paragraphs when length allows.
            - Never use explicit section labels, numbering, bullets, or list formatting.
            - Treat the preferred target as the practical goal for this retry.
            - If short, expand in this order: background -> role -> judgment -> execution detail -> outcome -> job connection.
            - Return JSON only with shape {"text":"..."}.
            """;

    private static final Map<String, Object> DRAFT_RESPONSE_SCHEMA = createDraftResponseSchema();

    private final String apiKey;
    private final String modelName;
    private final Duration timeout;
    private final ObjectMapper objectMapper;
    private final WorkspaceDraftAiService fallback;
    private final RestTemplate restTemplate;

    public OpenAiResponsesWorkspaceDraftService(
            String apiKey,
            String modelName,
            Duration timeout,
            ObjectMapper objectMapper,
            WorkspaceDraftAiService fallback
    ) {
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.timeout = timeout;
        this.objectMapper = objectMapper;
        this.fallback = fallback;
        this.restTemplate = buildRestTemplate(timeout);
    }

    @Override
    public DraftResponse generateDraft(
            String company,
            String position,
            String question,
            String companyContext,
            int maxLength,
            int minTarget,
            int maxTarget,
            String context,
            String others,
            String directive
    ) {
        int targetWindowMin = resolveTargetWindowMin(minTarget, maxTarget);
        int targetWindowMax = resolveTargetWindowMax(maxLength, maxTarget);
        String userPrompt = GENERATE_USER_PROMPT_TEMPLATE.formatted(
                safe(company),
                safe(position),
                safe(question),
                maxLength,
                minTarget,
                maxTarget,
                targetWindowMin,
                targetWindowMax,
                safe(companyContext),
                safe(context),
                safe(others),
                safeDirective(directive)
        );

        return requestDraft(
                GENERATE_SYSTEM_PROMPT,
                userPrompt,
                maxLength,
                minTarget,
                maxTarget,
                "generate",
                () -> fallback.generateDraft(company, position, question, companyContext, maxLength, minTarget, maxTarget, context, others, directive)
        );
    }

    @Override
    public DraftResponse refineDraft(
            String company,
            String position,
            String companyContext,
            String input,
            int maxLength,
            int minTarget,
            int maxTarget,
            String context,
            String others,
            String directive
    ) {
        int targetWindowMin = resolveTargetWindowMin(minTarget, maxTarget);
        int targetWindowMax = resolveTargetWindowMax(maxLength, maxTarget);
        boolean isLengthRetry = isLengthRetryDirective(directive);
        String userPromptTemplate = isLengthRetry ? REFINE_RETRY_USER_PROMPT_TEMPLATE : REFINE_USER_PROMPT_TEMPLATE;
        String userPrompt = userPromptTemplate.formatted(
                safe(company),
                safe(position),
                maxLength,
                minTarget,
                maxTarget,
                targetWindowMin,
                targetWindowMax,
                safe(companyContext),
                safe(input),
                safe(context),
                safe(others),
                safeDirective(directive)
        );

        return requestDraft(
                isLengthRetry ? REFINE_RETRY_SYSTEM_PROMPT : REFINE_SYSTEM_PROMPT,
                userPrompt,
                maxLength,
                minTarget,
                maxTarget,
                isLengthRetry ? "expand" : "refine",
                () -> fallback.refineDraft(company, position, companyContext, input, maxLength, minTarget, maxTarget, context, others, directive)
        );
    }

    @Override
    public DraftResponse shortenToLimit(
            String company,
            String position,
            String companyContext,
            String input,
            int maxLength,
            String context,
            String others
    ) {
        return fallback.shortenToLimit(company, position, companyContext, input, maxLength, context, others);
    }

    @Override
    public DraftResponse rewriteTitle(
            String company,
            String position,
            String question,
            String companyContext,
            String input,
            String context
    ) {
        return fallback.rewriteTitle(company, position, question, companyContext, input, context);
    }

    private DraftResponse requestDraft(
            String systemPrompt,
            String userPrompt,
            int maxLength,
            int minTarget,
            int maxTarget,
            String stage,
            FallbackCall fallbackCall
    ) {
        if (apiKey == null || apiKey.isBlank() || "demo".equals(apiKey)) {
            log.warn("OpenAI API key missing for Responses API draft call. Falling back to legacy chat model.");
            return fallbackCall.invoke();
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey.trim());

            Map<String, Object> requestBody = buildRequestBody(systemPrompt, userPrompt, maxLength, minTarget, maxTarget, stage);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            JsonNode response = restTemplate.postForObject(RESPONSES_API_URL, entity, JsonNode.class);

            if (response == null) {
                throw new IllegalStateException("Responses API returned no body");
            }

            DraftResponse draftResponse = parseDraftResponse(response);
            int actualChars = countVisibleCharacters(draftResponse.text);
            boolean underMin = minTarget > 0 && actualChars < minTarget;
            boolean overHard = maxLength > 0 && actualChars > maxLength;
            log.info("Responses API length metrics stage={} hardLimit={} minTarget={} preferredTarget={} actualChars={} underMin={} overHard={}",
                    stage, maxLength, minTarget, maxTarget, actualChars, underMin, overHard);
            return draftResponse;
        } catch (Exception e) {
            log.warn("Responses API draft call failed. Falling back to legacy chat model. model={}", modelName, e);
            return fallbackCall.invoke();
        }
    }

    private Map<String, Object> buildRequestBody(
            String systemPrompt,
            String userPrompt,
            int maxLength,
            int minTarget,
            int maxTarget,
            String stage
    ) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", modelName);
        requestBody.put("input", List.of(
                message("system", systemPrompt),
                message("user", userPrompt)
        ));
        requestBody.put("max_output_tokens", resolveMaxOutputTokens(maxLength, maxTarget));
        requestBody.put("metadata", Map.of(
                "pipeline_stage", safe(stage),
                "hard_limit", String.valueOf(maxLength),
                "min_target", String.valueOf(minTarget),
                "preferred_target", String.valueOf(maxTarget)
        ));
        requestBody.put("text", buildTextConfig());
        return requestBody;
    }

    private Map<String, Object> buildTextConfig() {
        Map<String, Object> textConfig = new LinkedHashMap<>();
        if (supportsVerbosity(modelName)) {
            textConfig.put("verbosity", "high");
        }

        Map<String, Object> format = new LinkedHashMap<>();
        format.put("type", "json_schema");
        format.put("name", "workspace_draft_response");
        format.put("strict", true);
        format.put("schema", DRAFT_RESPONSE_SCHEMA);
        textConfig.put("format", format);
        return textConfig;
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

    private int resolveTargetWindowMin(int minTarget, int preferredTarget) {
        int margin = Math.max(80, (int) Math.round(preferredTarget * 0.04));
        return Math.max(minTarget, preferredTarget - margin);
    }

    private int resolveTargetWindowMax(int maxLength, int preferredTarget) {
        int margin = Math.max(80, (int) Math.round(preferredTarget * 0.04));
        return Math.min(maxLength, preferredTarget + margin);
    }

    private int resolveMaxOutputTokens(int maxLength, int maxTarget) {
        int draftChars = Math.max(Math.max(maxLength, maxTarget), 1);
        int generousCap = Math.max(4096, draftChars * 3);
        return Math.min(generousCap, 8192);
    }

    private DraftResponse parseDraftResponse(JsonNode response) throws IOException {
        String outputText = extractOutputText(response).trim();
        DraftResponse draftResponse;
        if (outputText.startsWith("{")) {
            draftResponse = objectMapper.readValue(outputText, DraftResponse.class);
        } else {
            draftResponse = new DraftResponse();
            draftResponse.text = outputText;
        }

        if (draftResponse.text == null || draftResponse.text.isBlank()) {
            throw new IllegalStateException("Responses API draft payload did not contain text");
        }
        return draftResponse;
    }

    private String extractOutputText(JsonNode response) {
        JsonNode direct = response.get("output_text");
        if (direct != null && direct.isTextual() && !direct.asText().isBlank()) {
            return direct.asText();
        }

        StringBuilder builder = new StringBuilder();
        JsonNode outputs = response.path("output");
        if (outputs.isArray()) {
            outputs.forEach(item -> {
                JsonNode content = item.path("content");
                if (!content.isArray()) {
                    return;
                }
                content.forEach(part -> {
                    String type = part.path("type").asText("");
                    if (!"output_text".equals(type) && !"text".equals(type)) {
                        return;
                    }
                    String text = part.path("text").asText("");
                    if (text.isBlank()) {
                        return;
                    }
                    if (builder.length() > 0) {
                        builder.append('\n');
                    }
                    builder.append(text);
                });
            });
        }

        if (builder.length() == 0) {
            throw new IllegalStateException("Responses API returned no output_text content");
        }
        return builder.toString();
    }

    private static RestTemplate buildRestTemplate(Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, timeout.toMillis()));
        factory.setConnectTimeout(timeoutMillis);
        factory.setReadTimeout(timeoutMillis);
        return new RestTemplate(factory);
    }

    private static Map<String, Object> createDraftResponseSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("text", Map.of(
                "type", "string",
                "description", "Final Korean self-introduction answer"
        ));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("properties", properties);
        schema.put("required", List.of("text"));
        return schema;
    }

    private static boolean supportsVerbosity(String modelName) {
        return modelName != null && modelName.startsWith("gpt-5");
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "None" : value.trim();
    }

    private static String safeDirective(String directive) {
        return directive == null || directive.isBlank() ? "No extra user directive." : directive.trim();
    }

    private static boolean isLengthRetryDirective(String directive) {
        return directive != null && directive.contains(LENGTH_RETRY_MARKER);
    }

    private int countVisibleCharacters(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        return normalized.codePointCount(0, normalized.length());
    }

    @FunctionalInterface
    private interface FallbackCall {
        DraftResponse invoke();
    }
}

