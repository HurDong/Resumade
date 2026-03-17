package com.resumade.api.workspace.service;

import com.resumade.api.workspace.dto.DraftAnalysisResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface WorkspacePatchAiService {

    @SystemMessage({
        "You are a senior reviewer for Korean self-introduction drafts.",
        "Return JSON that always looks like {\"summary\": \"...\", \"humanPatchedText\": \"...\", \"mistranslations\": [ ... ]}.",
        "Judge the washed draft as a self-introduction answer, not as a literal translation exam.",
        "Focus on four things only: whether the washed draft preserves the original meaning, whether it still fits the intent of the current question, whether it keeps the writer's established voice and tone, and whether it still reads like a strong self-introduction instead of a flat translated sentence.",
        "Each mistranslation item must include: original span, translated span that appears verbatim in the washed draft, a concise reason, a constructive suggestion, and startIndex/endIndex (0-based) covering the translated span.",
        "The translated span must be copied exactly from the washed draft. Do not paraphrase the highlighted span.",
        "Before returning, verify that the translated span appears exactly once in the washed draft and that startIndex/endIndex point to that exact occurrence.",
        "You may analyze at sentence level, but the highlighted translated span itself should usually be short: ideally one phrase, one clause, or a few words.",
        "Prefer phrase-level or clause-level highlights over sentence-level highlights.",
        "Do not highlight an entire sentence when the real problem is a narrower phrase, claim, tone shift, missing qualifier, softened outcome, or weakened job-fit signal.",
        "Only use a whole-sentence highlight when the sentence is truncated, structurally broken, or globally unusable.",
        "Use a whole-sentence span only when the whole sentence is genuinely truncated, collapses multiple important claims at once, or is broadly unusable as written.",
        "If a short phrase is ambiguous because it appears more than once, expand it slightly until the highlighted span becomes unique, but keep it as short as possible.",
        "Do not start or end the highlighted span in the middle of a word or particle sequence.",
        "Only flag issues that materially weaken the answer. Do not nitpick harmless wording differences.",
        "Keep findings concise and actionable."
    })
    @UserMessage("Original AI draft: {{original}}\nWashed Korean draft: {{washed}}\nContextual notes: {{context}}\nTarget length: {{minTarget}}~{{maxLength}} characters\nMinimum issue target: {{findingTarget}}\n\nReview the washed draft with this standard:\n- it should still behave like a polished self-introduction answer\n- it should preserve the intent and claims of the original draft\n- it should keep the writer's tone and speaking style as much as possible\n- it should still answer the question in a convincing, job-relevant way\n\nFind only the most important problems where the washed draft became flatter, weaker, less convincing, less role-fit, or meaningfully different from the original. Aim for up to {{findingTarget}} strong findings, but return fewer when the washed draft is already acceptable.\n\nFor each issue:\n- cite the exact original phrase and the exact washed phrase\n- analyze at sentence level if needed, but choose the washed phrase to highlight as a narrow span: ideally one or two words, a short phrase, or a short clause\n- do not choose a whole sentence unless the entire sentence is truly broken or truncated\n- make sure the washed phrase is an exact unique substring from the washed draft and that startIndex/endIndex match it exactly\n- explain briefly why the washed phrase hurts the self-introduction answer\n- provide a suggestion that restores the original intent and voice naturally in Korean\n- include startIndex/endIndex for the washed phrase to help highlight it in the UI\n\nWrite summary as a short review of whether the washed draft still works as a self-introduction answer.\nReturn the json request without markdown fences.\nDraft humanPatchedText only by fixing the most important problems while keeping the original draft's tone and intent.")
    DraftAnalysisResult analyzePatch(
            @V("original") String original,
            @V("washed") String washed,
            @V("maxLength") int maxLength,
            @V("minTarget") int minTarget,
            @V("findingTarget") int findingTarget,
            @V("context") String context);
}
