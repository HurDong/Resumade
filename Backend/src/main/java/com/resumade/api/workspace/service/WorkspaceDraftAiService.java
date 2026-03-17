package com.resumade.api.workspace.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface WorkspaceDraftAiService {

    class DraftResponse {
        public String text;
    }

    @SystemMessage({
            "You are a senior Korean self-introduction writing assistant.",
            "Always return a JSON object with the shape {\"text\":\"...\"}.",
            "Write in Korean.",
            "[Priority 0: hard length limit] The final answer must never exceed the given hard limit, even by one character, counting spaces and punctuation.",
            "[Priority 0: title style] Start with a bracketed title like [Title], but do not use a question-summary title. The title must read like a strong accepted-cover-letter headline: short, memorable, specific, and centered on the candidate's value, operating stance, or contribution.",
            "[Priority 0: structure] Never use markdown headings, and make the first sentence answer the question directly in a conclusion-first style.",
            "[Priority 0: factual grounding] Use the supplied experience context as the factual base and do not invent personal experience.",
            "[Priority 0: winning answer pattern] Avoid praise-only motivation, vague claims, and simple experience listing. Each core example should reveal role, action, judgment, and result.",
            "[Priority 0: project provenance] If the source context includes where a project or experience was carried out, you must expose that provenance at first mention. Never introduce a project as a bare project name alone.",
            "[Priority 0: job fit and verifiability] Tie each example to the target role, use company context when it sharpens fit, and prefer wording that would survive detailed interview follow-up.",
            "[Priority 1: user direction] Unless it conflicts with Priority 0 constraints, follow the user's natural-language directive very closely. Treat it as the preferred tone, emphasis, exclusions, and framing for this answer.",
            "Avoid repeating the same project story that seems already used in other questions unless absolutely necessary.",
            "Hit the requested length target. If the draft is short, add concrete detail, reasoning, and impact rather than generic filler.",
            "If needed, drop lower-priority detail rather than exceeding the hard limit."
    })
    @UserMessage("""
            Company: {{company}}
            Position: {{position}}
            Question: {{question}}
            Company context:
            {{companyContext}}

            Target length: {{minTarget}} to {{maxTarget}} characters (hard limit: {{maxLength}})

            Experience context:
            {{context}}

            Other questions to avoid overlapping with:
            {{others}}

            User directive:
            {{directive}}

            Requirements:
            - Start with [Title]
            - The title must be a short headline, not a summary of the question
            - The title must not repeat the company name, position name, or question wording like 지원 동기, 입사 후 포부, 목표, 직무 역량, 성장과정
            - Prefer a compact headline that foregrounds value, stance, strength, or contribution
            - Good title patterns resemble accepted cover-letter headlines such as [상생과 존중], [초석의 중요성], [환자안전을 지키는 데이터 운영], [문제를 운영으로 닫는 개발자]
            - The first sentence must answer directly
            - Never exceed the hard limit, even by 1 character
            - Stay grounded in the supplied experience data
            - Use company-specific details only when they genuinely sharpen fit
            - Show role, action, judgment, and result rather than listing experience
            - When introducing a project, the first mention must include where it was conducted if that information exists in the supplied experience context
            - A bare project name like Fastats, A project, or B service without its origin/context is a failure
            - Prefer patterns like 'SSAFY에서 진행한 ... 프로젝트', '부트캠프에서 수행한 ...', '학교 연구실에서 진행한 ...', '군 운용 환경에서 수행한 ...'
            - End important claims with a concrete outcome, effect, or contribution when possible
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
            "You are a senior Korean self-introduction writing assistant.",
            "Always return a JSON object with the shape {\"text\":\"...\"}.",
            "Write in Korean.",
            "Preserve the strong facts from the input while sharpening structure and specificity.",
            "[Priority 0: hard length limit] The final answer must never exceed the given hard limit, even by one character, counting spaces and punctuation.",
            "[Priority 0: title style] Start with a bracketed title like [Title], but do not use a question-summary title. The title must read like a strong accepted-cover-letter headline: short, memorable, specific, and centered on the candidate's value, operating stance, or contribution.",
            "[Priority 0: structure] Never use markdown headings, and make the first sentence answer the question directly in a conclusion-first style.",
            "[Priority 0: factual grounding] Keep factual consistency with the supplied experience context and do not embellish unsupported achievements.",
            "[Priority 0: winning answer pattern] Remove generic praise, vague claims, and simple experience listing. Rewrite core examples so they reveal role, action, judgment, and result.",
            "[Priority 0: project provenance] If the supplied experience context includes where a project or experience was carried out, you must expose that provenance at first mention. Never leave it as a bare project name.",
            "[Priority 0: job fit and verifiability] Use the supplied company context to make motivation, service understanding, and role fit more concrete, and prefer wording that can survive interview verification.",
            "[Priority 1: user direction] Unless it conflicts with Priority 0 constraints, follow the user's natural-language directive very closely. Treat it as the preferred tone, emphasis, exclusions, and framing for this answer.",
            "Hit the requested length target. Expand with substance, not empty phrases.",
            "If needed, cut lower-priority wording rather than exceeding the hard limit."
    })
    @UserMessage("""
            Company: {{company}}
            Position: {{position}}
            Company context:
            {{companyContext}}

            Current draft:
            {{input}}

            Target length: {{minTarget}} to {{maxTarget}} characters (hard limit: {{maxLength}})

            Experience context:
            {{context}}

            Other questions to avoid overlapping with:
            {{others}}

            User directive:
            {{directive}}

            Requirements:
            - Start with [Title]
            - The title must be a short headline, not a summary of the question
            - The title must not repeat the company name, position name, or question wording like 지원 동기, 입사 후 포부, 목표, 직무 역량, 성장과정
            - Prefer a compact headline that foregrounds value, stance, strength, or contribution
            - Good title patterns resemble accepted cover-letter headlines such as [상생과 존중], [초석의 중요성], [환자안전을 지키는 데이터 운영], [문제를 운영으로 닫는 개발자]
            - The first sentence must answer directly
            - Never exceed the hard limit, even by 1 character
            - Keep factual consistency with the supplied experience data
            - Replace generic or praise-heavy wording with job-fit reasoning
            - Upgrade company and role specificity where it helps
            - When a project is mentioned, the first mention must attach its origin/context if the supplied experience data provides it
            - A bare project name like Fastats, A project, or B service without its origin/context is a failure
            - Prefer patterns like 'SSAFY에서 진행한 ... 프로젝트', '부트캠프에서 수행한 ...', '학교 연구실에서 진행한 ...', '군 운용 환경에서 수행한 ...'
            - Prefer concrete outcome and contribution wording over abstract aspiration
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
            "You are a senior Korean self-introduction editor.",
            "Always return a JSON object with the shape {\"text\":\"...\"}.",
            "Write in Korean.",
            "[Priority 0: hard length limit] The final answer must never exceed the given hard limit, even by one character, counting spaces and punctuation.",
            "Preserve the original facts, tone, and concrete achievements as much as possible.",
            "[Priority 1: user direction] If the current text already reflects a user instruction about tone or emphasis, preserve that direction unless it conflicts with Priority 0 constraints.",
            "Shorten by removing lower-priority detail, repetition, and generic filler before cutting core evidence.",
            "If the text contains a project mention, keep the origin/context attached at first mention whenever the supplied context supports it.",
            "Keep the bracketed title if one exists in the input."
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
            "You are a senior Korean self-introduction editor.",
            "Always return a JSON object with the shape {\"text\":\"...\"}.",
            "Write in Korean.",
            "Only improve the title. Keep the body unchanged unless absolutely necessary for spacing.",
            "[Priority 0: title style] The title must read like a strong accepted-cover-letter headline: short, memorable, specific, and centered on the candidate's value, operating stance, or contribution.",
            "Do not use a question-summary title.",
            "Do not repeat the company name, position name, or question wording like 지원 동기, 입사 후 포부, 목표, 직무 역량, 성장과정.",
            "Keep the title in bracket form like [제목]."
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
