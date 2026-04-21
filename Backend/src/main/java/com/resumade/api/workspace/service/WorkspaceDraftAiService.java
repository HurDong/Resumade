package com.resumade.api.workspace.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface WorkspaceDraftAiService {

    class DraftResponse {
        public String title;
        public String text;
    }

    class TitleCandidate {
        public String title;
        public Integer score;
        public String reason;
    }

    class TitleCandidatesResponse {
        public java.util.List<TitleCandidate> candidates;
    }

    @SystemMessage({
            "You write Korean self-introduction answers.",
            "Always return a JSON object with the shape {\"title\":\"...\",\"text\":\"...\"}.",
            "The \"title\" field contains only the title text — no brackets, no surrounding quotes inside the value.",
            "The \"text\" field contains only the body content — do NOT repeat the title inside the text.",
            "Count only the value of the text field. Do not count braces, quotes, key names, escape characters, or the title field.",
            "Write in Korean.",
            "The title must read like a concrete cover-letter headline grounded in action, result, role fit, or contribution.",
            "Prefer action + result, problem + resolution, or role + concrete value over a short slogan.",
            "The title must be a noun-phrase or action-outcome headline. Never end the title with a future-tense promise such as ~겠습니다, ~하겠습니다, ~만들겠습니다, ~이루겠습니다, or any equivalent vow or pledge form.",
            "Do not summarize the question or repeat the company name, position name, or question wording.",
            "Avoid generic meta titles such as 성장 경험, 문제 해결, 협업 역량, 지원동기, or similar labels.",
            "The first sentence must answer the question directly in a conclusion-first way.",
            "Use only facts and technologies supported by the supplied experience context. Do not invent experience, metrics, or unlisted tools.",
            "Read the Question Intent block first and obey its weighting rule.",
            "Treat company context, JD insight, and raw JD as the primary role-fit rubric only when the Question Intent block indicates job-fit or motivation is primary.",
            "If the Question Intent block indicates collaboration, growth, culture-fit, trend-insight, or problem-solving is primary, prioritize that question intent first and use JD only as a secondary alignment layer.",
            "Identify the most relevant 1-2 competencies, attitudes, or collaboration signals implied by the combined Question Intent and JD context and make the answer prove them with evidence.",
            "Treat the supplied other-questions context as a hard anti-overlap constraint, not a soft suggestion.",
            "Do not reuse the same main project, same first-sentence claim, same bracket title, or the same action-result arc already used in another question when another credible angle exists.",
            "If another question already uses a project, prefer a different project or a clearly different sub-problem, role, and evidence for this question.",
            "Prefer wording that can survive detailed interview follow-up.",
            "Each core example should show role, judgment, action, and result.",
            "Think in STAR or CARE internally, but never expose framework labels or section names in the final answer.",
            "Write as a natural Korean cover-letter narrative, not as a report, summary sheet, or presentation note.",
            "Do not structure the text as a series of labeled sections. This applies to ALL label-colon heading patterns without exception, including but not limited to: 문제:, 원인:, 원인 진단:, 검토:, 실행:, 교훈:, 결론:, 배경:, 상황:, (역할: ...), (결정: ...), (실행: ...), (결과: ...), [배경], [행동], [성과], or any equivalent. Write as flowing connected paragraphs throughout.",
            "Do not write first-second-third style mechanical enumeration unless the user explicitly requests it.",
            "If problem, cause, action, or result is missing, rewrite until the story is complete.",
            "When a project is first mentioned, include its origin or provenance if the context provides it.",
            "Avoid ceremonial openings, report-style labels, and list formatting unless the user explicitly asks for them.",
            "Do not narrate the answer like an outside evaluator with phrases equivalent to 'this case shows' or 'this experience demonstrates'; write in the applicant's own reflective voice.",
            "Prioritize natural Korean self-introduction prose over visibly neat structure.",
            "Do not drift into a promise-heavy future essay without enough evidence from past actions.",
            "Keep the voice believable for a new-grad or junior applicant: emphasize learning agility, bounded ownership, and interview-verifiable local impact over inflated senior-level claims.",
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
            Target text-field window: {{minTarget}} to {{maxTarget}} visible characters

            Experience context:
            {{context}}

            Other questions to avoid overlapping with:
            {{others}}

            User directive:
            {{directive}}

            Requirements:
            - Return {"title":"...","text":"..."} — title field has no brackets, text field has body only
            - Count only the value of the text field for the character limit
            - Do not count JSON braces, quotes, key names, or escape characters
            - Do not repeat the title inside the text field
            - Read the Question Intent block first and obey its weighting rule
            - Use company context, JD insight, and raw JD as the primary rubric only when the Question Intent block indicates job-fit or motivation is primary
            - If the Question Intent block indicates collaboration, growth, culture-fit, trend-insight, or problem-solving is primary, prioritize that intent first and use JD as a secondary tie-back
            - Infer the most relevant 1-2 competencies, attitudes, or collaboration signals from the combined Question Intent and JD context and center the answer on proving them
            - If the retrieved experience is weakly related to those priorities, reshape the answer toward stronger evidence rather than writing a generic story
            - Treat the "other questions" block as a hard anti-overlap constraint
            - Do not reuse the same main project, title, opening claim, or action-result storyline already used in another question unless explicitly required
            - If another question already uses a project, prefer a different project or a clearly different sub-problem, role, and evidence
            - The title must read like a concrete cover-letter headline grounded in action, result, role fit, or contribution
            - Prefer action + result, problem + resolution, or role + concrete value over a short slogan
            - The title must not summarize the question or repeat the company, position, or question wording
            - Avoid generic meta titles such as 성장 경험, 문제 해결, 협업 역량, 지원동기, or similar labels
            - Answer directly in the first sentence of the text body
            - Follow the requested paragraph structure or technical depth if provided
            - Use only facts and technologies supported by the experience context
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
            "Count only the value of the text field. Do not count braces, quotes, key names, or escape characters.",
            "Write in Korean.",
            "Preserve the strong facts from the current draft while improving structure, specificity, and job fit.",
            "Keep the bracketed title format.",
            "The title must read like a concrete cover-letter headline grounded in action, result, role fit, or contribution.",
            "Prefer action + result, problem + resolution, or role + concrete value over a short slogan.",
            "Do not summarize the question or repeat the company name, position name, or question wording.",
            "Avoid generic meta titles such as [성장 경험], [문제 해결], [협업 역량], [지원동기], or similar labels.",
            "The first sentence must answer the question directly in a conclusion-first way.",
            "Use only facts and technologies supported by the supplied experience context. Do not invent experience, metrics, or unlisted tools.",
            "Read the Question Intent block first and obey its weighting rule.",
            "Treat company context, JD insight, and raw JD as the primary role-fit rubric only when the Question Intent block indicates job-fit or motivation is primary.",
            "If the Question Intent block indicates collaboration, growth, culture-fit, trend-insight, or problem-solving is primary, prioritize that question intent first and use JD only as a secondary alignment layer.",
            "Identify the most relevant 1-2 competencies, attitudes, or collaboration signals implied by the combined Question Intent and JD context and make the answer prove them with evidence.",
            "Treat the supplied other-questions context as a hard anti-overlap constraint, not a soft suggestion.",
            "Do not reuse the same main project, same first-sentence claim, same bracket title, or the same action-result arc already used in another question when another credible angle exists.",
            "If another question already uses a project, prefer a different project or a clearly different sub-problem, role, and evidence for this question.",
            "Prefer wording that can survive detailed interview follow-up.",
            "Each core example should show role, judgment, action, and result.",
            "Think in STAR or CARE internally, but never expose framework labels or section names in the final answer.",
            "Write as a natural Korean cover-letter narrative, not as a report, summary sheet, or presentation note.",
            "Do not structure the text as a series of labeled sections. This applies to ALL label-colon heading patterns without exception, including but not limited to: 문제:, 원인:, 원인 진단:, 검토:, 실행:, 교훈:, 결론:, 배경:, 상황:, (역할: ...), (결정: ...), (실행: ...), (결과: ...), [배경], [행동], [성과], or any equivalent. Write as flowing connected paragraphs throughout.",
            "Do not write first-second-third style mechanical enumeration unless the user explicitly requests it.",
            "If problem, cause, action, or result is missing, rewrite until the story is complete.",
            "When a project is first mentioned, include its origin or provenance if the context provides it.",
            "Avoid ceremonial openings, report-style labels, and list formatting unless the user explicitly asks for them.",
            "Do not narrate the answer like an outside evaluator with phrases equivalent to 'this case shows' or 'this experience demonstrates'; write in the applicant's own reflective voice.",
            "Prioritize natural Korean self-introduction prose over visibly neat structure.",
            "Do not drift into a promise-heavy future essay without enough evidence from past actions.",
            "Keep the voice believable for a new-grad or junior applicant: emphasize learning agility, bounded ownership, and interview-verifiable local impact over inflated senior-level claims.",
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
            Target text-field window: {{minTarget}} to {{maxTarget}} visible characters

            Experience context:
            {{context}}

            Other questions to avoid overlapping with:
            {{others}}

            User directive:
            {{directive}}

            Requirements:
            - Count only the value of the text field
            - Do not count JSON braces, quotes, key names, or escape characters
            - Start with [Title]
            - Read the Question Intent block first and obey its weighting rule
            - Use company context, JD insight, and raw JD as the primary rubric only when the Question Intent block indicates job-fit or motivation is primary
            - If the Question Intent block indicates collaboration, growth, culture-fit, trend-insight, or problem-solving is primary, prioritize that intent first and use JD as a secondary tie-back
            - Infer the most relevant 1-2 competencies, attitudes, or collaboration signals from the combined Question Intent and JD context and revise the current draft to prove them more clearly
            - Treat the "other questions" block as a hard anti-overlap constraint
            - Do not reuse the same main project, title, opening claim, or action-result storyline already used in another question unless explicitly required
            - If another question already uses a project, prefer a different project or a clearly different sub-problem, role, and evidence
            - The title must read like a concrete cover-letter headline grounded in action, result, role fit, or contribution
            - Prefer action + result, problem + resolution, or role + concrete value over a short slogan
            - The title must not summarize the question or repeat the company, position, or question wording
            - Avoid generic meta titles such as [성장 경험], [문제 해결], [협업 역량], [지원동기], or similar labels
            - Answer directly in the first sentence
            - Follow the requested paragraph structure or technical depth if provided
            - Use only facts and technologies supported by the experience context
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
            "This is structure-preserving compression, not summarization or rewriting from scratch.",
            "Preserve the same core claim, same main experience, same evidence order, and same conclusion unless the input itself is invalid.",
            "Do not replace the selected experience with a different one.",
            "Do not move the conclusion, action, or result into a different narrative role.",
            "Keep project origin or provenance attached at first mention whenever the supplied context supports it.",
            "Shorten by removing repetition, generic filler, and lower-priority detail before cutting core evidence.",
            "Delete first: repeated modifiers, generic promises, ceremonial openings, over-explained background, and low-priority adjectives.",
            "Preserve a natural Korean self-introduction narrative instead of converting the text into report-like labels or list items.",
            "Do not introduce parenthetical meta labels such as (역할: ...), (결정: ...), (실행: ...), or (결과: ...).",
            "Do not introduce commentator phrasing like '이 사례는 ~를 보여줍니다' while shortening.",
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
            Compress the current text so it stays within the hard limit while preserving the existing structure, strongest facts, role fit, and concrete outcomes.

            Requirements:
            - Never exceed the hard limit, even by 1 character
            - Preserve the same core claim, same main experience, same evidence order, and same conclusion
            - Do not summarize into a different answer
            - Do not change the selected project, problem, action, or result
            - Keep factual consistency with the supplied experience data
            - Prefer deleting repetitive or lower-priority detail over weakening the core point
            - Keep the text natural and interview-verifiable
            - Do not convert the text into report labels, section markers, or parenthetical summaries
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
            "The title must reflect BOTH the question's core theme AND the key evidence or trait shown in the body.",
            "Read the question carefully and extract its core intent (e.g., personality trait, collaboration style, growth mindset, problem-solving approach). Weave that intent into the title alongside the concrete evidence from the body.",
            "Do not merely copy the question wording — distill the underlying trait or quality the question is probing, and express it through the applicant's demonstrated behavior or outcome.",
            "Prefer: [trait or quality implied by question] + [concrete action or result from body]. Examples: [꼼꼼함이 팀 배포 장애를 막다], [빠른 학습으로 낯선 기술 스택을 3주 만에 내 것으로].",
            "Internally draft multiple candidate titles and choose the most natural one.",
            "Use a metric only when it is explicitly supported by the current text or supplied context.",
            "Do not repeat the company name, position name, or exact question wording verbatim.",
            "Do not use first-person pronouns such as 저는 or 저의.",
            "Do not turn the title into a report label or a meta category such as [역할], [결정], [성과 요약], [성장 경험], [문제 해결], [협업 역량], or similar.",
            "Avoid vague buzzwords such as 성실, 열정, 노력, 도전정신, or bare nouns like 경험, 역량, 성장, 협업 unless they are anchored by a specific action or outcome."
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
            Rewrite only the title line so it captures BOTH the question's underlying theme and the body's key evidence.

            Requirements:
            - Keep the body content unchanged
            - Replace only the title line
            - Internally draft 4 to 6 different title candidates and choose the single best one
            - Step 1: identify what trait, quality, or competency the question is really asking about
            - Step 2: identify the strongest concrete evidence (action, outcome, or behavior) in the body
            - Step 3: fuse both into one memorable headline — the trait expressed through the evidence
            - The new title should usually be around 18 to 28 Korean characters inside the brackets when natural
            - Do not copy question wording verbatim — express the underlying intent through behavior or outcome
            - Use a metric only when it is explicitly supported by the current text or supplied context
            - Avoid generic labels, vague buzzwords, or bare meta categories
            - Return only the full updated text in the required JSON shape
            """)
    DraftResponse rewriteTitle(
            @V("company") String company,
            @V("position") String position,
            @V("question") String question,
            @V("companyContext") String companyContext,
            @V("input") String input,
            @V("context") String context);

    @SystemMessage({
            "You write Korean self-introduction titles.",
            "Always return a JSON object with the shape {\"candidates\":[{\"title\":\"...\",\"score\":0,\"reason\":\"...\"}]}",
            "Write in Korean.",
            "Generate 4 to 5 title candidates.",
            "Each title must stay in bracket form like [Title].",
            "Each title must read like a concrete cover-letter headline grounded in action, result, role fit, or contribution.",
            "Prefer action + result, problem + resolution, or role + concrete value over a short slogan.",
            "Use a metric only when it is explicitly supported by the current text or supplied context.",
            "Do not use question-summary titles.",
            "Do not repeat the company name, position name, or question wording.",
            "Do not use first-person pronouns.",
            "Do not turn titles into report labels or meta categories such as [역할], [결정], [결과 요약], [성장 경험], [문제 해결], [협업 역량], or similar.",
            "Avoid vague buzzwords or bare nouns unless anchored by a specific action or outcome.",
            "Score: an integer 0-100. Primary criterion is how well the title addresses the Question's evaluation intent. Secondary criterion is how well it is grounded in the body content. A title that ignores the question angle must score below 60 regardless of how strong the body content is.",
            "Reason: one concise Korean sentence that MUST explain (1) what the Question is evaluating and (2) how this title directly addresses that evaluation angle through the body's evidence. Do NOT write a reason that only describes why the title matches the body content.",
            "CRITICAL: The Question field is the primary constraint. Read it first. A title that showcases impressive content but misses what the question is asking is always wrong."
    })
    @UserMessage("""
            Company: {{company}}
            Position: {{position}}
            Question (primary constraint — read this first): {{question}}
            Company context:
            {{companyContext}}

            Current text:
            {{input}}

            Title strategy context:
            {{context}}

            Step 1 — Analyze the Question (do this before anything else):
            - What is the recruiter specifically testing through this question?
              (e.g., self-awareness of strengths AND weaknesses, growth mindset with a concrete plan,
               technical depth, motivation fit, collaboration style, problem-solving process)
            - If the question asks for multiple aspects (e.g., strength + weakness + improvement plan),
              identify which aspect should lead the title and which should be implied.
            - Write this evaluation intent down internally before proposing any title.

            Step 2 — Find the matching evidence:
            - Locate the part of Current text that most directly addresses the evaluation intent from Step 1.
            - This evidence — not the most technically impressive part — must anchor the title.

            Step 3 — Propose titles:
            - Each title must connect Step 1 (question's evaluation angle) with Step 2 (matching evidence).
            - A title that ignores what the question is asking is always wrong, even if it sounds impressive.

            Requirements:
            - Generate 4 to 5 candidates
            - Rank by fitness for this question's evaluation intent first, body evidence second
            - Keep the body unchanged; only propose title lines
            - Return only the required JSON shape
            """)
    TitleCandidatesResponse suggestTitles(
            @V("company") String company,
            @V("position") String position,
            @V("question") String question,
            @V("companyContext") String companyContext,
            @V("input") String input,
            @V("context") String context);
}
