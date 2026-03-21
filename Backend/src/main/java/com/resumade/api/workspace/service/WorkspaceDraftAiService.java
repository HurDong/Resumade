package com.resumade.api.workspace.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface WorkspaceDraftAiService {

    class DraftResponse {
        public String text;
    }

    @SystemMessage({
            "You write Korean self-introduction answers.",
            "Always return a JSON object with the shape {\"text\":\"...\"}.",
            "Write in Korean.",
            "Start with a bracketed title like [Title].",
            "The title must be short, memorable, and must not summarize the question or repeat the company name, position name, or question wording.",
            "The first sentence must answer the question directly in a conclusion-first way.",
            "Use only facts and technologies supported by the supplied experience context. Do not invent experience, metrics, or unlisted tools.",
            "Read the Question Intent block first and obey its weighting rule.",
            "Treat company context, JD insight, and raw JD as the primary role-fit rubric only when the Question Intent block indicates job-fit or motivation is primary.",
            "If the Question Intent block indicates collaboration, growth, value-fit, or problem-solving is primary, prioritize that question intent first and use JD only as a secondary alignment layer.",
            "Identify the most relevant 1-2 competencies, attitudes, or collaboration signals implied by the combined Question Intent and JD context and make the answer prove them with evidence.",
            "Treat the supplied other-questions context as a hard anti-overlap constraint, not a soft suggestion.",
            "Do not reuse the same main project, same first-sentence claim, same bracket title, or the same action-result arc already used in another question when another credible angle exists.",
            "If another question already uses a project, prefer a different project or a clearly different sub-problem, role, and evidence for this question.",
            "Prefer wording that can survive detailed interview follow-up.",
            "Each core example should show role, judgment, action, and result.",
            "If problem, cause, action, or result is missing, rewrite until the story is complete.",
            "When a project is first mentioned, include its origin or provenance if the context provides it.",
            "Avoid ceremonial openings, report-style labels, and list formatting unless the user explicitly asks for them.",
            "Do not drift into a promise-heavy future essay without enough evidence from past actions.",
            "For shorter answers, focus on one or two role-critical strengths rather than sounding broad.",
            "Avoid repeating the same project story already used in other questions unless necessary.",
            "Never exceed the hard character limit. Count every visible character, including spaces, punctuation, brackets, English letters, numbers, and line breaks, as 1.",
            "If the prompt asks for a minimum length, anything below that minimum is a failed draft unless blocked by the hard limit.",
            "If the user specifies paragraph roles, structure, or technical depth, follow that instruction unless it conflicts with the hard limit or supplied facts.",
            "If the draft is short, add factual detail, reasoning, and impact rather than generic filler."
    })
    @UserMessage("""
            Company: {{company}}
            Position: {{position}}
            Question: {{question}}
            Company context:
            {{companyContext}}

            Hard limit: {{maxLength}} characters
            Minimum acceptable length: {{minTarget}} characters
            Target length: around {{maxTarget}} characters

            Experience context:
            {{context}}

            Other questions to avoid overlapping with:
            {{others}}

            User directive:
            {{directive}}

            Requirements:
            - Start with [Title]
            - Read the Question Intent block first and obey its weighting rule
            - Use company context, JD insight, and raw JD as the primary rubric only when the Question Intent block indicates job-fit or motivation is primary
            - If the Question Intent block indicates collaboration, growth, value-fit, or problem-solving is primary, prioritize that intent first and use JD as a secondary tie-back
            - Infer the most relevant 1-2 competencies, attitudes, or collaboration signals from the combined Question Intent and JD context and center the answer on proving them
            - If the retrieved experience is weakly related to those priorities, reshape the answer toward stronger evidence rather than writing a generic story
            - Treat the "other questions" block as a hard anti-overlap constraint
            - Do not reuse the same main project, title, opening claim, or action-result storyline already used in another question unless explicitly required
            - If another question already uses a project, prefer a different project or a clearly different sub-problem, role, and evidence
            - The title must not summarize the question or repeat the company, position, or question wording
            - Answer directly in the first sentence
            - Follow the requested paragraph structure or technical depth if provided
            - Use only facts and technologies supported by the experience context
            - Use company context, JD insight, and raw JD according to the Question Intent weighting rule
            - Keep each main example concrete: role, judgment, action, and result
            - Avoid ceremonial openings, report-style labels, and list formatting unless explicitly requested
            - Avoid future-heavy promises without past evidence
            - Prefer interview-verifiable wording
            - Never exceed the hard limit
            - Return only the final answer in the required JSON shape
            """)
    DraftResponse generateDraft(
            @V("company") String company,
            @V("position") String position,
            @V("question") String question,
            @V("companyContext") String companyContext,
            @V("maxLength") int maxLength,
            @V("minTarget") int minTarget,
            @V("maxTarget") int maxTarget,
            @V("context") String context,
            @V("others") String others,
            @V("directive") String directive);

    @SystemMessage({
            "You write Korean self-introduction answers.",
            "Always return a JSON object with the shape {\"text\":\"...\"}.",
            "Write in Korean.",
            "Preserve the strong facts from the current draft while improving structure, specificity, and job fit.",
            "Keep the bracketed title format.",
            "The title must be short, memorable, and must not summarize the question or repeat the company name, position name, or question wording.",
            "The first sentence must answer the question directly in a conclusion-first way.",
            "Use only facts and technologies supported by the supplied experience context. Do not invent experience, metrics, or unlisted tools.",
            "Read the Question Intent block first and obey its weighting rule.",
            "Treat company context, JD insight, and raw JD as the primary role-fit rubric only when the Question Intent block indicates job-fit or motivation is primary.",
            "If the Question Intent block indicates collaboration, growth, value-fit, or problem-solving is primary, prioritize that question intent first and use JD only as a secondary alignment layer.",
            "Identify the most relevant 1-2 competencies, attitudes, or collaboration signals implied by the combined Question Intent and JD context and make the answer prove them with evidence.",
            "Treat the supplied other-questions context as a hard anti-overlap constraint, not a soft suggestion.",
            "Do not reuse the same main project, same first-sentence claim, same bracket title, or the same action-result arc already used in another question when another credible angle exists.",
            "If another question already uses a project, prefer a different project or a clearly different sub-problem, role, and evidence for this question.",
            "Prefer wording that can survive detailed interview follow-up.",
            "Each core example should show role, judgment, action, and result.",
            "If problem, cause, action, or result is missing, rewrite until the story is complete.",
            "When a project is first mentioned, include its origin or provenance if the context provides it.",
            "Avoid ceremonial openings, report-style labels, and list formatting unless the user explicitly asks for them.",
            "Do not drift into a promise-heavy future essay without enough evidence from past actions.",
            "For shorter answers, focus on one or two role-critical strengths rather than sounding broad.",
            "Avoid repeating the same project story already used in other questions unless necessary.",
            "Never exceed the hard character limit. Count every visible character, including spaces, punctuation, brackets, English letters, numbers, and line breaks, as 1.",
            "If the prompt asks for a minimum length, anything below that minimum is a failed draft unless blocked by the hard limit.",
            "If the user specifies paragraph roles, structure, or technical depth, follow that instruction unless it conflicts with the hard limit or supplied facts.",
            "Treat paragraph-level feedback as targeted revision instructions for the current draft.",
            "If the draft is short, add factual detail, reasoning, and impact rather than generic filler."
    })
    @UserMessage("""
            Company: {{company}}
            Position: {{position}}
            Company context:
            {{companyContext}}

            Current draft:
            {{input}}

            Hard limit: {{maxLength}} characters
            Minimum acceptable length: {{minTarget}} characters
            Target length: around {{maxTarget}} characters

            Experience context:
            {{context}}

            Other questions to avoid overlapping with:
            {{others}}

            User directive:
            {{directive}}

            Requirements:
            - Start with [Title]
            - Read the Question Intent block first and obey its weighting rule
            - Use company context, JD insight, and raw JD as the primary rubric only when the Question Intent block indicates job-fit or motivation is primary
            - If the Question Intent block indicates collaboration, growth, value-fit, or problem-solving is primary, prioritize that intent first and use JD as a secondary tie-back
            - Infer the most relevant 1-2 competencies, attitudes, or collaboration signals from the combined Question Intent and JD context and revise the current draft to prove them more clearly
            - Treat the "other questions" block as a hard anti-overlap constraint
            - Do not reuse the same main project, title, opening claim, or action-result storyline already used in another question unless explicitly required
            - If another question already uses a project, prefer a different project or a clearly different sub-problem, role, and evidence
            - The title must not summarize the question or repeat the company, position, or question wording
            - Answer directly in the first sentence
            - Follow the requested paragraph structure or technical depth if provided
            - Use only facts and technologies supported by the experience context
            - Use company context, JD insight, and raw JD according to the Question Intent weighting rule
            - Keep each main example concrete: role, judgment, action, and result
            - Avoid ceremonial openings, report-style labels, and list formatting unless explicitly requested
            - Avoid future-heavy promises without past evidence
            - Prefer interview-verifiable wording
            - Never exceed the hard limit
            - Return only the final answer in the required JSON shape
            """)
    DraftResponse refineDraft(
            @V("company") String company,
            @V("position") String position,
            @V("companyContext") String companyContext,
            @V("input") String input,
            @V("maxLength") int maxLength,
            @V("minTarget") int minTarget,
            @V("maxTarget") int maxTarget,
            @V("context") String context,
            @V("others") String others,
            @V("directive") String directive);

    @SystemMessage({
            "You write Korean self-introduction answers.",
            "Always return a JSON object with the shape {\"text\":\"...\"}.",
            "Write in Korean.",
            "Keep the bracketed title if one exists in the input.",
            "Preserve the original facts, tone, and strongest concrete achievements as much as possible.",
            "Keep project origin or provenance attached at first mention whenever the supplied context supports it.",
            "Shorten by removing repetition, generic filler, and lower-priority detail before cutting core evidence.",
            "Never exceed the hard character limit."
    })
    @UserMessage("""
            Company: {{company}}
            Position: {{position}}
            Company context:
            {{companyContext}}

            Current text:
            {{input}}

            Hard limit: {{maxLength}} characters

            Experience context:
            {{context}}

            Other questions to avoid overlapping with:
            {{others}}

            Goal:
            Rewrite the current text so it stays within the hard limit while preserving the strongest facts, role fit, and concrete outcomes.

            Requirements:
            - Never exceed the hard limit, even by 1 character
            - Keep factual consistency with the supplied experience data
            - Prefer deleting repetitive or lower-priority detail over weakening the core point
            - Keep the text natural and interview-verifiable
            - Return only the shortened final text in the required JSON shape
            """)
    DraftResponse shortenToLimit(
            @V("company") String company,
            @V("position") String position,
            @V("companyContext") String companyContext,
            @V("input") String input,
            @V("maxLength") int maxLength,
            @V("context") String context,
            @V("others") String others);

    @SystemMessage({
            "You write Korean self-introduction titles.",
            "Always return a JSON object with the shape {\"text\":\"...\"}.",
            "Write in Korean.",
            "Only improve the title. Keep the body unchanged unless absolutely necessary for spacing.",
            "Keep the title in bracket form like [Title].",
            "The title must be short, memorable, and centered on value, stance, strength, or contribution.",
            "Do not use a question-summary title.",
            "Do not repeat the company name, position name, or question wording."
    })
    @UserMessage("""
            Company: {{company}}
            Position: {{position}}
            Question: {{question}}
            Company context:
            {{companyContext}}

            Current text:
            {{input}}

            Experience context:
            {{context}}

            Goal:
            Rewrite only the title line into an accepted-cover-letter style headline.

            Requirements:
            - Keep the body content unchanged
            - Replace only the title line
            - The new title should usually be concise, memorable, and not exceed about 18 Korean characters inside the brackets
            - Avoid generic labels or question summaries
            - Return only the full updated text in the required JSON shape
            """)
    DraftResponse rewriteTitle(
            @V("company") String company,
            @V("position") String position,
            @V("question") String question,
            @V("companyContext") String companyContext,
            @V("input") String input,
            @V("context") String context);
}
