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
import java.util.Locale;
import java.util.Map;

@Slf4j
public class OpenAiResponsesWorkspaceDraftService implements WorkspaceDraftAiService {

    private static final String RESPONSES_API_URL = "https://api.openai.com/v1/responses";
    private static final String LENGTH_RETRY_MARKER = "[LENGTH_RETRY]";
    private static final double ESTIMATED_OUTPUT_TOKENS_PER_VISIBLE_CHAR = 1.45;
    private static final int OUTPUT_TOKEN_JSON_OVERHEAD = 96;
    private static final int MIN_OUTPUT_TOKEN_BUDGET = 700;
    private static final double GENERATE_REASONING_RESERVE_RATIO = 0.55;
    private static final double REFINE_REASONING_RESERVE_RATIO = 0.45;
    private static final double EXPAND_REASONING_RESERVE_RATIO = 0.30;
    private static final int GENERATE_REASONING_RESERVE_FLOOR = 480;
    private static final int REFINE_REASONING_RESERVE_FLOOR = 400;
    private static final int EXPAND_REASONING_RESERVE_FLOOR = 280;
    private static final int GENERATE_REASONING_RESERVE_CEILING = 960;
    private static final int REFINE_REASONING_RESERVE_CEILING = 820;
    private static final int EXPAND_REASONING_RESERVE_CEILING = 560;
    private static final int SHORT_OUTPUT_RETRY_LIMIT = 2;
    private static final double MIN_ACCEPTABLE_DRAFT_RATIO = 0.875;

    private static final String GENERATE_SYSTEM_PROMPT = """
            You write Korean self-introduction answers.
            Length is a hard requirement. Return a draft inside the requested character range, never below the minimum target, and aim for the upper end of the range.
            Return JSON only with exactly this shape: {"text":"..."}.
            Before returning, silently count visible characters including spaces and line breaks, and expand with concrete evidence until the draft reaches the range.
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
            Project-title overlap across questions is allowed only when the detailed topic is different.
            Do not reuse the same first-sentence claim, same bracket title, same detailed technical decision, same troubleshooting point, same lesson learned, same metric cluster, or the same action-result arc already used in another question when another credible angle exists.
            If another question already uses a project, prefer a clearly different sub-problem, role, evidence cluster, and takeaway for this question.
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
            Length is a hard requirement. Return a draft inside the requested character range, never below the minimum target, and aim for the upper end of the range.
            Return JSON only with exactly this shape: {"text":"..."}.
            Before returning, silently count visible characters including spaces and line breaks, and expand with concrete evidence until the draft reaches the range.
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
            Project-title overlap across questions is allowed only when the detailed topic is different.
            Do not reuse the same first-sentence claim, same bracket title, same detailed technical decision, same troubleshooting point, same lesson learned, same metric cluster, or the same action-result arc already used in another question when another credible angle exists.
            If another question already uses a project, prefer a clearly different sub-problem, role, evidence cluster, and takeaway for this question.
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
            Length is a hard requirement. Return a draft inside the requested character range, never below the minimum target, and aim for the upper end of the range.
            Return JSON only with exactly this shape: {"text":"..."}.
            Before returning, silently count visible characters including spaces and line breaks, and expand with concrete evidence until the draft reaches the range.
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
            Project-title overlap across questions is allowed only when the detailed topic is different.
            Do not reuse the same first-sentence claim, same bracket title, same detailed technical decision, same troubleshooting point, same lesson learned, same metric cluster, or the same action-result arc already used in another question when another credible angle exists.
            If another question already uses a project, prefer a clearly different sub-problem, role, evidence cluster, and takeaway for this question.
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
            Required output range: %d to %d characters
            Target zone: stay close to the upper end of the range without exceeding the hard limit

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
            - Project-title overlap across questions is allowed only when the detailed topic is different
            - Do not reuse the same title, opening claim, detailed technical decision, troubleshooting point, lesson learned, metric cluster, or action-result storyline already used in another question unless explicitly required
            - If another question already uses a project, prefer a clearly different sub-problem, role, evidence cluster, and takeaway
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
            - If the draft is too short, expand in this order: background -> role -> judgment -> execution detail -> measurable result -> job connection
            - Never exceed the hard limit
            - Outputs shorter than the required range are invalid
            - Return only the final answer in the required JSON shape
            """;

    private static final String REFINE_USER_PROMPT_TEMPLATE = """
            Company: %s
            Position: %s
            Hard limit: %d characters
            Required output range: %d to %d characters
            Target zone: stay close to the upper end of the range without exceeding the hard limit

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
            - Project-title overlap across questions is allowed only when the detailed topic is different
            - Do not reuse the same title, opening claim, detailed technical decision, troubleshooting point, lesson learned, metric cluster, or action-result storyline already used in another question unless explicitly required
            - If another question already uses a project, prefer a clearly different sub-problem, role, evidence cluster, and takeaway
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
            - If the draft is too short, expand in this order: background -> role -> judgment -> execution detail -> measurable result -> job connection
            - Never exceed the hard limit
            - Outputs shorter than the required range are invalid
            - Return only the final answer in the required JSON shape
            """;

    private static final String REFINE_RETRY_USER_PROMPT_TEMPLATE = """
            Company: %s
            Position: %s
            Hard limit: %d characters
            Required output range: %d to %d characters
            Target zone: stay close to the upper end of the range without exceeding the hard limit

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
            - Project-title overlap across questions is allowed only when the detailed topic is different
            - Do not reuse the same title, opening claim, detailed technical decision, troubleshooting point, lesson learned, metric cluster, or action-result storyline already used in another question unless explicitly required
            - If another question already uses a project, prefer a clearly different sub-problem, role, evidence cluster, and takeaway
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
            - If the draft is too short, expand in this order: background -> role -> judgment -> execution detail -> measurable result -> job connection
            - Never exceed the hard limit
            - Outputs shorter than the required range are invalid
            - Return only the final answer in the required JSON shape
            """;

    private static final Map<String, Object> DRAFT_RESPONSE_SCHEMA = createDraftResponseSchema();
    private static final String COMPACT_BASE_SYSTEM_PROMPT = """
            You write Korean self-introduction answers in Korean.
            Return JSON only with exactly this shape: {"text":"..."}.
            Count only the value of the text field. Do not count braces, quotes, key names, or escape characters.
            Aim for the requested target window and stay below the hard limit with margin. Treat over-limit output as invalid.
            Start with a bracketed title like [Title].
            The title must be short and must not repeat the company, position, or question wording.
            The first sentence must answer the question directly.
            Use only facts and technologies supported by the supplied experience context or explicit user directive. Do not invent experience, metrics, or unlisted tools.
            Read the Question Intent block first. Use company context, JD insight, and raw JD as the primary rubric only when the question is mainly about job fit or motivation. Otherwise use them as a secondary alignment layer.
            Center the answer on 1-2 relevant competencies and prove them with concrete role, judgment, action, and result.
            Treat the supplied other-questions context as a hard anti-overlap constraint.
            Write natural Korean cover-letter prose. Do not use report labels, section markers, or mechanical first-second-third enumeration unless explicitly requested.
            Prefer interview-verifiable wording.
            If the answer is short, add factual depth and impact instead of filler.
            """;
    private static final String COMPACT_GENERATE_NOTE = """
            Treat the explicit user directive as higher priority than retrieved emphasis unless it would require invented facts.
            """;
    private static final String COMPACT_REFINE_NOTE = """
            Preserve the strong facts from the current draft while improving structure, specificity, and job fit.
            Treat paragraph-level feedback as targeted revision instructions for the current draft.
            """;
    private static final String COMPACT_RETRY_NOTE = """
            The previous result missed the target window.
            Preserve all strong facts from the current draft and expand only the missing depth.
            Treat paragraph-level feedback as targeted revision instructions for the current draft.
            """;

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
        String userPrompt = buildGenerateUserPrompt(
                company,
                position,
                question,
                companyContext,
                maxLength,
                minTarget,
                maxTarget,
                context,
                others,
                directive
        );

        return requestDraft(
                buildCompactSystemPrompt("generate", false),
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
        String userPrompt = buildRefineUserPrompt(
                company,
                position,
                companyContext,
                input,
                maxLength,
                minTarget,
                maxTarget,
                context,
                others,
                directive,
                isLengthRetry
        );

        return requestDraft(
                buildCompactSystemPrompt("refine", isLengthRetry),
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

    @Override
    public TitleCandidatesResponse suggestTitles(
            String company,
            String position,
            String question,
            String companyContext,
            String input,
            String context
    ) {
        return fallback.suggestTitles(company, position, question, companyContext, input, context);
    }

    private String buildCompactSystemPrompt(String stage, boolean isLengthRetry) {
        StringBuilder builder = new StringBuilder(COMPACT_BASE_SYSTEM_PROMPT.trim());
        String stageNote = "generate".equalsIgnoreCase(stage)
                ? COMPACT_GENERATE_NOTE
                : (isLengthRetry ? COMPACT_RETRY_NOTE : COMPACT_REFINE_NOTE);
        if (stageNote != null && !stageNote.isBlank()) {
            builder.append("\n\n");
            builder.append(stageNote.trim());
        }
        return builder.toString();
    }

    private String buildGenerateUserPrompt(
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
        return """
                Company: %s
                Position: %s
                Question: %s
                Hard limit: %d characters
                Target text-field window: %d to %d visible characters
                Priority: land inside the target window and stay below the hard limit with margin. Over-limit output is invalid.

                Company context:
                %s

                Experience context:
                %s

                Other questions to avoid overlapping with:
                %s

                Priority user directive:
                %s

                Final checks:
                - Count only the value of the text field
                - Do not count JSON braces, quotes, key names, or escape characters
                - Obey the user directive first unless it would require invented facts
                - Follow any requested paragraph structure or technical depth if present
                - Return JSON only in the required shape
                """.formatted(
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
    }

    private String buildRefineUserPrompt(
            String company,
            String position,
            String companyContext,
            String input,
            int maxLength,
            int minTarget,
            int maxTarget,
            String context,
            String others,
            String directive,
            boolean isLengthRetry
    ) {
        String retryBlock = isLengthRetry
                ? """

                Retry note:
                - The previous output missed the target window
                - Preserve strong existing facts and expand only missing depth
                """
                : "";

        return """
                Company: %s
                Position: %s
                Hard limit: %d characters
                Target text-field window: %d to %d visible characters
                Priority: land inside the target window and stay below the hard limit with margin. Over-limit output is invalid.

                Company context:
                %s

                Current draft:
                %s

                Experience context:
                %s

                Other questions to avoid overlapping with:
                %s

                Priority user directive:
                %s%s

                Final checks:
                - Count only the value of the text field
                - Do not count JSON braces, quotes, key names, or escape characters
                - Obey the user directive first unless it would require invented facts
                - Follow any requested paragraph structure or technical depth if present
                - Return JSON only in the required shape
                """.formatted(
                safe(company),
                safe(position),
                maxLength,
                minTarget,
                maxTarget,
                safe(companyContext),
                safe(input),
                safe(context),
                safe(others),
                safeDirective(directive),
                retryBlock
        );
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

        String promptForAttempt = userPrompt;
        for (int attempt = 0; attempt <= SHORT_OUTPUT_RETRY_LIMIT; attempt++) {
            JsonNode response = null;
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(apiKey.trim());

                Map<String, Object> requestBody = buildRequestBody(systemPrompt, promptForAttempt, maxLength, minTarget, maxTarget, stage);
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
                response = restTemplate.postForObject(RESPONSES_API_URL, entity, JsonNode.class);

                if (response == null) {
                    throw new IllegalStateException("Responses API returned no body");
                }

                if (shouldFallbackToLegacy(response)) {
                    log.warn(
                            "Responses API exhausted output budget before emitting draft content. Falling back to legacy chat model. stage={} model={} reason={} outputTokens={} reasoningTokens={}",
                            stage,
                            response.path("model").asText(modelName),
                            response.path("incomplete_details").path("reason").asText("unknown"),
                            response.path("usage").path("output_tokens").asInt(-1),
                            response.path("usage").path("output_tokens_details").path("reasoning_tokens").asInt(-1));
                    return fallbackCall.invoke();
                }

                DraftResponse draftResponse = parseDraftResponse(response);
                int actualChars = countVisibleCharacters(draftResponse.text);
                String status = resolveLengthStatus(actualChars, minTarget, maxLength);
                log.info("{} AI 초안 생성 | 단계={} | 상태={} | 초안군=- | 시도=- | 글자수={}자 | 목표={}~{}자 | 제한={}자 | 다음={} | 모델={}",
                        resolveStageIcon(stage, status),
                        toKoreanStage(stage),
                        toKoreanStatus(status),
                        actualChars,
                        minTarget,
                        maxTarget,
                        maxLength,
                        "워크스페이스로 반환",
                        modelName);

                if (!shouldRetryForShortOutput(stage, actualChars, minTarget) || attempt == SHORT_OUTPUT_RETRY_LIMIT) {
                    return draftResponse;
                }

                int acceptableFloor = resolveAcceptableRetryFloor(minTarget);
                int missingChars = Math.max(1, acceptableFloor - actualChars);
                log.warn(
                        "Responses API draft was far below the acceptable floor. Retrying stage={} attempt={} actualChars={} acceptableFloor={} minTarget={} maxTarget={}",
                        stage,
                        attempt + 1,
                        actualChars,
                        acceptableFloor,
                        minTarget,
                        maxTarget);
                promptForAttempt = buildShortOutputRetryPrompt(promptForAttempt, actualChars, acceptableFloor, maxTarget, missingChars);
            } catch (Exception e) {
                logResponseFailureDetails(stage, response, e);
                if (isIncompleteMaxOutputTokens(response)) {
                    log.warn(
                            "Responses API draft call hit max_output_tokens after partial content. Falling back to legacy chat model. stage={} model={} outputTokens={} reasoningTokens={}",
                            stage,
                            response.path("model").asText(modelName),
                            response.path("usage").path("output_tokens").asInt(-1),
                            response.path("usage").path("output_tokens_details").path("reasoning_tokens").asInt(-1));
                } else {
                    log.warn("Responses API draft call failed. Falling back to legacy chat model. model={}", modelName, e);
                }
                return fallbackCall.invoke();
            }
        }
        return fallbackCall.invoke();
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
        requestBody.put("max_output_tokens", resolveMaxOutputTokens(maxLength, maxTarget, stage));
        requestBody.put("metadata", Map.of(
                "pipeline_stage", safe(stage),
                "hard_limit", String.valueOf(maxLength),
                "target_window_start", String.valueOf(minTarget),
                "target_window_end", String.valueOf(maxTarget)
        ));
        Map<String, Object> reasoningConfig = buildReasoningConfig(stage);
        if (reasoningConfig != null && !reasoningConfig.isEmpty()) {
            requestBody.put("reasoning", reasoningConfig);
        }
        requestBody.put("text", buildTextConfig());
        return requestBody;
    }

    private Map<String, Object> buildTextConfig() {
        Map<String, Object> textConfig = new LinkedHashMap<>();
        if (supportsVerbosity(modelName)) {
            textConfig.put("verbosity", "low");
        }

        Map<String, Object> format = new LinkedHashMap<>();
        format.put("type", "json_schema");
        format.put("name", "workspace_draft_response");
        format.put("strict", true);
        format.put("schema", DRAFT_RESPONSE_SCHEMA);
        textConfig.put("format", format);
        return textConfig;
    }

    private Map<String, Object> buildReasoningConfig(String stage) {
        if (!"generate".equalsIgnoreCase(stage)
                && !"refine".equalsIgnoreCase(stage)
                && !"expand".equalsIgnoreCase(stage)) {
            return null;
        }

        String effort = resolveReasoningEffort(modelName);
        if (effort == null || effort.isBlank()) {
            return null;
        }

        return Map.of("effort", effort);
    }

    private String resolveReasoningEffort(String currentModelName) {
        if (currentModelName == null || currentModelName.isBlank()) {
            return null;
        }

        String normalized = currentModelName.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("gpt-5-mini")) {
            // For short schema-constrained draft calls, medium reasoning often spends the entire
            // output budget before the model emits the JSON payload.
            return "low";
        }
        if (normalized.startsWith("gpt-5.4-pro")) {
            return "medium";
        }
        if (normalized.startsWith("gpt-5-pro") || normalized.startsWith("gpt-5.2-pro")) {
            return "high";
        }
        if (normalized.startsWith("gpt-5.1")
                || normalized.startsWith("gpt-5.2")
                || normalized.startsWith("gpt-5.3")
                || normalized.startsWith("gpt-5.4")) {
            return "none";
        }
        if ("gpt-5".equals(normalized) || "gpt-5-2025-08-07".equals(normalized)) {
            return "minimal";
        }
        return null;
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

    private int resolveMaxOutputTokens(int maxLength, int maxTarget, String stage) {
        int referenceChars = Math.max(1, maxTarget > 0 ? maxTarget : maxLength);
        int answerTokenBudget = OUTPUT_TOKEN_JSON_OVERHEAD
                + (int) Math.ceil(referenceChars * ESTIMATED_OUTPUT_TOKENS_PER_VISIBLE_CHAR);
        int reasoningReserve = resolveReasoningReserve(answerTokenBudget, stage);

        return Math.max(MIN_OUTPUT_TOKEN_BUDGET, answerTokenBudget + reasoningReserve);
    }

    private int resolveReasoningReserve(int answerTokenBudget, String stage) {
        String normalizedStage = stage == null ? "" : stage.trim().toLowerCase(Locale.ROOT);

        double ratio;
        int floor;
        int ceiling;
        switch (normalizedStage) {
            case "generate" -> {
                ratio = GENERATE_REASONING_RESERVE_RATIO;
                floor = GENERATE_REASONING_RESERVE_FLOOR;
                ceiling = GENERATE_REASONING_RESERVE_CEILING;
            }
            case "expand" -> {
                ratio = EXPAND_REASONING_RESERVE_RATIO;
                floor = EXPAND_REASONING_RESERVE_FLOOR;
                ceiling = EXPAND_REASONING_RESERVE_CEILING;
            }
            case "refine" -> {
                ratio = REFINE_REASONING_RESERVE_RATIO;
                floor = REFINE_REASONING_RESERVE_FLOOR;
                ceiling = REFINE_REASONING_RESERVE_CEILING;
            }
            default -> {
                ratio = REFINE_REASONING_RESERVE_RATIO;
                floor = REFINE_REASONING_RESERVE_FLOOR;
                ceiling = REFINE_REASONING_RESERVE_CEILING;
            }
        }

        int dynamicReserve = (int) Math.ceil(answerTokenBudget * ratio);
        return Math.max(floor, Math.min(ceiling, dynamicReserve));
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

    private void logFinalPrompt(
            String stage,
            String systemPrompt,
            String userPrompt,
            int maxLength,
            int minTarget,
            int maxTarget
    ) {
        log.warn("""
                ===== RESPONSES API FINAL PROMPT =====
                stage: {}
                model: {}
                hardLimit: {}
                minTarget: {}
                preferredTarget: {}
                ----- SYSTEM PROMPT -----
                {}
                ----- USER PROMPT -----
                {}
                ===== END FINAL PROMPT =====
                """,
                stage,
                modelName,
                maxLength,
                minTarget,
                maxTarget,
                systemPrompt,
                userPrompt
        );
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

    private boolean shouldFallbackToLegacy(JsonNode response) {
        if (response == null) {
            return false;
        }

        if (!isIncompleteMaxOutputTokens(response)) {
            return false;
        }

        JsonNode outputs = response.path("output");
        if (!outputs.isArray() || outputs.isEmpty()) {
            return true;
        }

        for (JsonNode item : outputs) {
            String outputType = item.path("type").asText("");
            if ("message".equalsIgnoreCase(outputType) || "output_text".equalsIgnoreCase(outputType)) {
                return true;
            }

            if (!"reasoning".equalsIgnoreCase(outputType)) {
                return false;
            }
        }

        return true;
    }

    private boolean isIncompleteMaxOutputTokens(JsonNode response) {
        if (response == null) {
            return false;
        }

        String status = response.path("status").asText("");
        String incompleteReason = response.path("incomplete_details").path("reason").asText("");
        return "incomplete".equalsIgnoreCase(status)
                && "max_output_tokens".equalsIgnoreCase(incompleteReason);
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

    private boolean shouldRetryForShortOutput(String stage, int actualChars, int minTarget) {
        if (minTarget <= 0 || actualChars >= minTarget) {
            return false;
        }

        String normalizedStage = stage == null ? "" : stage.trim().toLowerCase(Locale.ROOT);
        if (!"generate".equals(normalizedStage)
                && !"refine".equals(normalizedStage)
                && !"expand".equals(normalizedStage)) {
            return false;
        }

        return actualChars < resolveAcceptableRetryFloor(minTarget);
    }

    private int resolveAcceptableRetryFloor(int minTarget) {
        return Math.max(1, (int) Math.ceil(minTarget * MIN_ACCEPTABLE_DRAFT_RATIO));
    }

    private String buildShortOutputRetryPrompt(
            String originalPrompt,
            int actualChars,
            int acceptableFloor,
            int maxTarget,
            int missingChars) {
        return originalPrompt + """

                
                Retry correction:
                - The previous output was invalid because it was only %d visible characters.
                - The next output must be at least %d visible characters.
                - Add at least %d more visible characters with concrete facts, reasoning, obstacles, actions, and outcomes.
                - Do not summarize or compress the draft.
                - Use denser evidence and explanation until the answer reaches the requested window.
                - Outputs below %d visible characters are invalid.
                - Aim for %d visible characters if natural.
                """.formatted(
                actualChars,
                acceptableFloor,
                Math.max(1, missingChars),
                acceptableFloor,
                Math.max(acceptableFloor, maxTarget));
    }

    private String resolveLengthStatus(int actualChars, int minTarget, int hardLimit) {
        if (hardLimit > 0 && actualChars > hardLimit) {
            return "OVER_LIMIT";
        }
        if (minTarget > 0 && actualChars < minTarget) {
            return "UNDER_MIN";
        }
        return "IN_RANGE";
    }

    private String toKoreanStage(String stage) {
        String normalized = stage == null ? "" : stage.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "GENERATE" -> "초안 생성";
            case "REFINE" -> "초안 수정";
            case "EXPAND" -> "초안 확장";
            default -> normalized;
        };
    }

    private String toKoreanStatus(String status) {
        return switch (status) {
            case "UNDER_MIN" -> "최소 글자수 미달";
            case "OVER_LIMIT" -> "최대 글자수 초과";
            case "IN_RANGE" -> "목표 구간 충족";
            default -> status;
        };
    }

    private String resolveStageIcon(String stage, String status) {
        if ("UNDER_MIN".equals(status) || "OVER_LIMIT".equals(status)) {
            return "⚠️";
        }
        if ("IN_RANGE".equals(status)) {
            return "✅";
        }
        String normalized = stage == null ? "" : stage.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "GENERATE", "REFINE", "EXPAND" -> "📝";
            default -> "ℹ️";
        };
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
