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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class OpenAiResponsesWorkspaceDraftService implements WorkspaceDraftAiService {

    private static final String RESPONSES_API_URL = "https://api.openai.com/v1/responses";
    private static final String LENGTH_RETRY_MARKER = "[LENGTH_RETRY]";

    private static final String GENERATE_SYSTEM_PROMPT = """
            You write Korean self-introduction answers.
            Return JSON only with exactly this shape: {"text":"..."}.
            Write in Korean.
            Start with a bracketed title like [Title].
            The title must be short, memorable, and must not summarize the question or repeat the company name, position name, or question wording.
            The first sentence must answer the question directly in a conclusion-first way.
            Use only facts and technologies supported by the supplied experience context or explicit user directive. Do not invent experience, metrics, or unlisted tools.
            Read the Question Intent block first and obey its weighting rule.
            Treat company context, JD insight, and raw JD as the primary role-fit rubric only when the Question Intent block indicates job-fit or motivation is primary.
            If the Question Intent block indicates collaboration, growth, value-fit, or problem-solving is primary, prioritize that question intent first and use JD only as a secondary alignment layer.
            Identify the most relevant 1-2 competencies, attitudes, or collaboration signals implied by the combined Question Intent and JD context and make the answer prove them with evidence.
            If the explicit user directive says to foreground, suppress, or avoid certain experiences, roles, or technologies, follow that directive over retrieved-context emphasis.
            Treat the supplied "other questions" context as a hard anti-overlap constraint, not a soft suggestion.
            Do not reuse the same main project, same first-sentence claim, same bracket title, or the same action-result arc already used in another question when another credible angle exists.
            If another question already uses a project, prefer a different project or clearly different sub-problem, role, and evidence for this question.
            Prefer wording that can survive detailed interview follow-up.
            Each core example should show role, judgment, action, and result.
            Think in STAR or CARE internally, but never expose framework labels or section names in the final answer.
            Write as a natural Korean cover-letter narrative, not as a report, summary sheet, or presentation note.
            Do not use parenthetical meta labels or bracketed field labels such as (역할: ...), (결정: ...), (실행: ...), (결과: ...), [배경], [행동], or [성과].
            Do not write first-second-third style mechanical enumeration unless the user explicitly requests it.
            If problem, cause, action, or result is missing, rewrite until the story is complete.
            When a project is first mentioned, include its origin or provenance if the context provides it.
            Avoid ceremonial openings, report-style labels, and list formatting unless the user explicitly asks for them.
            Do not narrate the answer like an outside evaluator with phrases equivalent to 'this case shows' or 'this experience demonstrates'; write in the applicant's own reflective voice.
            Prioritize natural Korean self-introduction prose over visibly neat structure.
            Do not drift into a promise-heavy future essay without enough evidence from past actions.
            For shorter answers, focus on one or two role-critical strengths rather than sounding broad.
            Avoid repeating the same project story already used in other questions unless necessary.
            Never exceed the hard character limit. Count every visible character, including spaces, punctuation, brackets, English letters, numbers, and line breaks, as 1.
            If the prompt asks for a minimum length, anything below that minimum is a failed draft unless blocked by the hard limit.
            If the user specifies paragraph roles, structure, or technical depth, follow that instruction unless it conflicts with the hard limit or supplied facts.
            If the draft is short, add factual detail, reasoning, and impact rather than generic filler.
            """;

    private static final String REFINE_SYSTEM_PROMPT = """
            You write Korean self-introduction answers.
            Return JSON only with exactly this shape: {"text":"..."}.
            Write in Korean.
            Preserve the strong facts from the current draft while improving structure, specificity, and job fit.
            Keep the bracketed title format.
            The title must be short, memorable, and must not turn into a generic question summary or repeat the company name or position name.
            The first sentence must answer the question directly in a conclusion-first way.
            Use only facts and technologies supported by the supplied experience context, current draft, or explicit user directive. Do not invent experience, metrics, or unlisted tools.
            Read the Question Intent block first and obey its weighting rule.
            Treat company context, JD insight, and raw JD as the primary role-fit rubric only when the Question Intent block indicates job-fit or motivation is primary.
            If the Question Intent block indicates collaboration, growth, value-fit, or problem-solving is primary, prioritize that question intent first and use JD only as a secondary alignment layer.
            Identify the most relevant 1-2 competencies, attitudes, or collaboration signals implied by the combined Question Intent and JD context and make the answer prove them with evidence.
            If the explicit user directive says to foreground, suppress, or avoid certain experiences, roles, or technologies, follow that directive over retrieved-context emphasis.
            Treat the supplied "other questions" context as a hard anti-overlap constraint, not a soft suggestion.
            Do not reuse the same main project, same first-sentence claim, same bracket title, or the same action-result arc already used in another question when another credible angle exists.
            If another question already uses a project, prefer a different project or clearly different sub-problem, role, and evidence for this question.
            Prefer wording that can survive detailed interview follow-up.
            Each core example should show role, judgment, action, and result.
            Think in STAR or CARE internally, but never expose framework labels or section names in the final answer.
            Write as a natural Korean cover-letter narrative, not as a report, summary sheet, or presentation note.
            Do not use parenthetical meta labels or bracketed field labels such as (역할: ...), (결정: ...), (실행: ...), (결과: ...), [배경], [행동], or [성과].
            Do not write first-second-third style mechanical enumeration unless the user explicitly requests it.
            If problem, cause, action, or result is missing, rewrite until the story is complete.
            When a project is first mentioned, include its origin or provenance if the context provides it.
            Avoid ceremonial openings, report-style labels, and list formatting unless the user explicitly asks for them.
            Do not narrate the answer like an outside evaluator with phrases equivalent to 'this case shows' or 'this experience demonstrates'; write in the applicant's own reflective voice.
            Prioritize natural Korean self-introduction prose over visibly neat structure.
            Do not drift into a promise-heavy future essay without enough evidence from past actions.
            For shorter answers, focus on one or two role-critical strengths rather than sounding broad.
            Avoid repeating the same project story already used in other questions unless necessary.
            Never exceed the hard character limit. Count every visible character, including spaces, punctuation, brackets, English letters, numbers, and line breaks, as 1.
            If the prompt asks for a minimum length, anything below that minimum is a failed draft unless blocked by the hard limit.
            If the user specifies paragraph roles, structure, or technical depth, follow that instruction unless it conflicts with the hard limit or supplied facts.
            Treat paragraph-level feedback as targeted revision instructions for the current draft.
            If the draft is short, add factual detail, reasoning, and impact rather than generic filler.
            """;

    private static final String REFINE_RETRY_SYSTEM_PROMPT = """
            You write Korean self-introduction answers.
            Return JSON only with exactly this shape: {"text":"..."}.
            Write in Korean.
            The previous result was below the minimum length target.
            Keep all strong facts from the current draft and expand only the missing depth.
            Keep the bracketed title format.
            The title must stay concise and must not turn into a generic question summary or repeat the company name or position name.
            The first sentence must answer the question directly in a conclusion-first way.
            Use only facts and technologies supported by the supplied experience context, current draft, or explicit user directive. Do not invent experience, metrics, or unlisted tools.
            Read the Question Intent block first and obey its weighting rule.
            Treat company context, JD insight, and raw JD as the primary role-fit rubric only when the Question Intent block indicates job-fit or motivation is primary.
            If the Question Intent block indicates collaboration, growth, value-fit, or problem-solving is primary, prioritize that question intent first and use JD only as a secondary alignment layer.
            Identify the most relevant 1-2 competencies, attitudes, or collaboration signals implied by the combined Question Intent and JD context and make the answer prove them with evidence.
            If the explicit user directive says to foreground, suppress, or avoid certain experiences, roles, or technologies, follow that directive over retrieved-context emphasis.
            Treat the supplied "other questions" context as a hard anti-overlap constraint, not a soft suggestion.
            Do not reuse the same main project, same first-sentence claim, same bracket title, or the same action-result arc already used in another question when another credible angle exists.
            If another question already uses a project, prefer a different project or clearly different sub-problem, role, and evidence for this question.
            Prefer wording that can survive detailed interview follow-up.
            Each core example should show role, judgment, action, and result.
            Think in STAR or CARE internally, but never expose framework labels or section names in the final answer.
            Write as a natural Korean cover-letter narrative, not as a report, summary sheet, or presentation note.
            Do not use parenthetical meta labels or bracketed field labels such as (역할: ...), (결정: ...), (실행: ...), (결과: ...), [배경], [행동], or [성과].
            Do not write first-second-third style mechanical enumeration unless the user explicitly requests it.
            If problem, cause, action, or result is missing, rewrite until the story is complete.
            When a project is first mentioned, include its origin or provenance if the context provides it.
            Avoid ceremonial openings, report-style labels, and list formatting unless the user explicitly asks for them.
            Do not narrate the answer like an outside evaluator with phrases equivalent to 'this case shows' or 'this experience demonstrates'; write in the applicant's own reflective voice.
            Prioritize natural Korean self-introduction prose over visibly neat structure.
            Do not drift into a promise-heavy future essay without enough evidence from past actions.
            Never summarize, compress, or weaken already strong factual sentences.
            Never exceed the hard character limit. Count every visible character, including spaces, punctuation, brackets, English letters, numbers, and line breaks, as 1.
            If the prompt asks for a minimum length, anything below that minimum is a failed draft unless blocked by the hard limit.
            If the user specifies paragraph roles, structure, or technical depth, follow that instruction unless it conflicts with the hard limit or supplied facts.
            Treat paragraph-level feedback as targeted revision instructions for the current draft.
            Add factual detail, reasoning, and impact rather than generic filler.
            """;

    private static final String GENERATE_USER_PROMPT_TEMPLATE = """
            Company: %s
            Position: %s
            Question: %s
            Hard limit: %d characters
            Minimum acceptable length: %d characters
            Target length: around %d characters

            Company context:
            %s

            Experience context:
            %s

            Other questions to avoid overlapping with:
            %s

            Priority user directive:
            %s

            Requirements:
            - Treat the user directive as the highest-priority writing instruction
            - If the user directive conflicts with retrieved-context emphasis, follow the user directive unless it would require inventing facts beyond the directive
            - Read the Question Intent block first and obey its weighting rule
            - Use company context, JD insight, and raw JD as the primary rubric only when the Question Intent block indicates job-fit or motivation is primary
            - If the Question Intent block indicates collaboration, growth, value-fit, or problem-solving is primary, prioritize that intent first and use JD as a secondary tie-back
            - Infer the most relevant 1-2 competencies, attitudes, or collaboration signals from the combined Question Intent and JD context and center the answer on proving them
            - If the retrieved experience is weakly related to those priorities, reshape the answer toward stronger evidence rather than writing a generic story
            - Treat the "other questions" block as a hard anti-overlap constraint
            - Do not reuse the same main project, title, opening claim, or action-result storyline already used in another question unless explicitly required
            - If another question already uses a project, prefer a different project or a clearly different sub-problem, role, and evidence
            - Start with [Title]
            - The title must not summarize the question or repeat the company, position, or question wording
            - Answer directly in the first sentence
            - Follow the requested paragraph structure or technical depth if provided
            - Use only facts and technologies supported by the experience context or explicit user directive
            - Use company context, JD insight, and raw JD according to the Question Intent weighting rule
            - Keep each main example concrete: role, judgment, action, and result
            - Think in STAR or CARE internally, but never expose structure labels in the final answer
            - Write as a natural Korean self-introduction narrative, not a report or 발표문
            - Do not use parenthetical meta labels like (역할: ...), (결정: ...), (실행: ...), (결과: ...)
            - Do not expose labels such as [배경], [행동], [성과], or similar section markers
            - Do not use first-second-third style mechanical enumeration unless explicitly requested
            - Avoid ceremonial openings, report-style labels, and list formatting unless explicitly requested
            - Do not use commentator phrases like '이 사례는 ~를 보여줍니다' when the point can be stated directly in the applicant's voice
            - Avoid future-heavy promises without past evidence
            - Prefer interview-verifiable wording
            - Never exceed the hard limit
            - Return only the final answer in the required JSON shape
            """;

    private static final String REFINE_USER_PROMPT_TEMPLATE = """
            Company: %s
            Position: %s
            Hard limit: %d characters
            Minimum acceptable length: %d characters
            Target length: around %d characters

            Company context:
            %s

            Current draft:
            %s

            Experience context:
            %s

            Other questions to avoid overlapping with:
            %s

            Priority user directive:
            %s

            Requirements:
            - Treat the user directive as the highest-priority revision instruction
            - If the user directive conflicts with retrieved-context emphasis, follow the user directive unless it would require inventing facts beyond the directive
            - Read the Question Intent block first and obey its weighting rule
            - Use company context, JD insight, and raw JD as the primary rubric only when the Question Intent block indicates job-fit or motivation is primary
            - If the Question Intent block indicates collaboration, growth, value-fit, or problem-solving is primary, prioritize that intent first and use JD as a secondary tie-back
            - Infer the most relevant 1-2 competencies, attitudes, or collaboration signals from the combined Question Intent and JD context and revise the current draft to prove them more clearly
            - Treat the "other questions" block as a hard anti-overlap constraint
            - Do not reuse the same main project, title, opening claim, or action-result storyline already used in another question unless explicitly required
            - If another question already uses a project, prefer a different project or a clearly different sub-problem, role, and evidence
            - Start with [Title]
            - Keep the title concise and non-generic
            - Answer directly in the first sentence
            - Follow the requested paragraph structure or technical depth if provided
            - Use only facts and technologies supported by the experience context, current draft, or explicit user directive
            - Use company context, JD insight, and raw JD according to the Question Intent weighting rule
            - Keep each main example concrete: role, judgment, action, and result
            - Think in STAR or CARE internally, but never expose structure labels in the final answer
            - Write as a natural Korean self-introduction narrative, not a report or 발표문
            - Do not use parenthetical meta labels like (역할: ...), (결정: ...), (실행: ...), (결과: ...)
            - Do not expose labels such as [배경], [행동], [성과], or similar section markers
            - Do not use first-second-third style mechanical enumeration unless explicitly requested
            - Avoid ceremonial openings, report-style labels, and list formatting unless explicitly requested
            - Do not use commentator phrases like '이 사례는 ~를 보여줍니다' when the point can be stated directly in the applicant's voice
            - Avoid future-heavy promises without past evidence
            - Prefer interview-verifiable wording
            - Never exceed the hard limit
            - Return only the final answer in the required JSON shape
            """;

    private static final String REFINE_RETRY_USER_PROMPT_TEMPLATE = """
            Company: %s
            Position: %s
            Hard limit: %d characters
            Minimum acceptable length: %d characters
            Target length: around %d characters

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
            - Treat the retry feedback and embedded user directive as the highest-priority revision instruction
            - If the user directive conflicts with retrieved-context emphasis, follow the user directive unless it would require inventing facts beyond the directive
            - Read the Question Intent block first and obey its weighting rule
            - Use company context, JD insight, and raw JD as the primary rubric only when the Question Intent block indicates job-fit or motivation is primary
            - If the Question Intent block indicates collaboration, growth, value-fit, or problem-solving is primary, prioritize that intent first and use JD as a secondary tie-back
            - Infer the most relevant 1-2 competencies, attitudes, or collaboration signals from the combined Question Intent and JD context and revise the current draft to prove them more clearly
            - Treat the "other questions" block as a hard anti-overlap constraint
            - Do not reuse the same main project, title, opening claim, or action-result storyline already used in another question unless explicitly required
            - If another question already uses a project, prefer a different project or a clearly different sub-problem, role, and evidence
            - The previous output was under the minimum length target. Fix this in this retry.
            - Preserve all strong facts already present
            - Expand only missing depth; do not summarize or compress existing strong content
            - Keep [Title] and a direct first sentence
            - Use only facts and technologies supported by the experience context, current draft, or explicit user directive
            - Use company context, JD insight, and raw JD according to the Question Intent weighting rule
            - Keep each main example concrete: role, judgment, action, and result
            - Think in STAR or CARE internally, but never expose structure labels in the final answer
            - Write as a natural Korean self-introduction narrative, not a report or 발표문
            - Do not use parenthetical meta labels like (역할: ...), (결정: ...), (실행: ...), (결과: ...)
            - Do not expose labels such as [배경], [행동], [성과], or similar section markers
            - Do not use first-second-third style mechanical enumeration unless explicitly requested
            - Avoid ceremonial openings, report-style labels, and list formatting unless explicitly requested
            - Do not use commentator phrases like '이 사례는 ~를 보여줍니다' when the point can be stated directly in the applicant's voice
            - Avoid future-heavy promises without past evidence
            - Prefer interview-verifiable wording
            - Never exceed the hard limit
            - Return only the final answer in the required JSON shape
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
        String userPrompt = GENERATE_USER_PROMPT_TEMPLATE.formatted(
                safe(company),
                safe(position),
                safe(question),
                maxLength,
                minTarget,
                maxTarget,
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
        boolean isLengthRetry = isLengthRetryDirective(directive);
        String userPromptTemplate = isLengthRetry ? REFINE_RETRY_USER_PROMPT_TEMPLATE : REFINE_USER_PROMPT_TEMPLATE;
        String userPrompt = userPromptTemplate.formatted(
                safe(company),
                safe(position),
                maxLength,
                minTarget,
                maxTarget,
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

        JsonNode response = null;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey.trim());

            Map<String, Object> requestBody = buildRequestBody(systemPrompt, userPrompt, maxLength, minTarget, maxTarget, stage);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            response = restTemplate.postForObject(RESPONSES_API_URL, entity, JsonNode.class);

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
            logResponseFailureDetails(stage, response, e);
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
        if ("generate".equalsIgnoreCase(stage)
                || "refine".equalsIgnoreCase(stage)
                || "expand".equalsIgnoreCase(stage)) {
            requestBody.put("reasoning", Map.of("effort", "low"));
        }
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
            throw new IllegalStateException("Responses API returned no parseable output content. body="
                    + abbreviateForLog(response.toString(), 2000));
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
            String nestedText = extractNodeText(text);
            if (!nestedText.isBlank()) {
                return nestedText;
            }
        }

        if (node.isObject() || node.isArray()) {
            return node.toString();
        }

        return node.asText("");
    }

    private String abbreviateForLog(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private void logResponseFailureDetails(String stage, JsonNode response, Exception error) {
        if (response == null) {
            return;
        }

        String status = response.path("status").asText("");
        String incompleteReason = response.path("incomplete_details").path("reason").asText("");
        int outputTokens = response.path("usage").path("output_tokens").asInt(-1);
        int reasoningTokens = response.path("usage").path("output_tokens_details").path("reasoning_tokens").asInt(-1);

        if ("incomplete".equalsIgnoreCase(status) && !incompleteReason.isBlank()) {
            log.warn(
                    "Responses API incomplete response stage={} model={} reason={} outputTokens={} reasoningTokens={} outputTypes={}",
                    stage,
                    response.path("model").asText(modelName),
                    incompleteReason,
                    outputTokens,
                    reasoningTokens,
                    summarizeOutputTypes(response.path("output"))
            );
        }

        if (error instanceof IllegalStateException) {
            log.warn(
                    "Responses API parse failure stage={} model={} status={} outputTypes={} body={}",
                    stage,
                    response.path("model").asText(modelName),
                    status.isBlank() ? "unknown" : status,
                    summarizeOutputTypes(response.path("output")),
                    abbreviateForLog(response.toString(), 2000)
            );
        }
    }

    private String summarizeOutputTypes(JsonNode outputs) {
        if (!outputs.isArray() || outputs.isEmpty()) {
            return "[]";
        }

        StringBuilder summary = new StringBuilder("[");
        for (int i = 0; i < outputs.size(); i++) {
            if (i > 0) {
                summary.append(", ");
            }
            summary.append(outputs.get(i).path("type").asText("unknown"));
        }
        summary.append(']');
        return summary.toString();
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
