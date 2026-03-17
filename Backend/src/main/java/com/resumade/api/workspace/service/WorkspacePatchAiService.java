package com.resumade.api.workspace.service;

import com.resumade.api.workspace.dto.DraftAnalysisResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface WorkspacePatchAiService {

    @SystemMessage({
        "You are a senior Korean-English technical writer who detects translation drift and tone issues in candidate statements.",
        "Return JSON that always looks like {\"summary\": \"...\", \"humanPatchedText\": \"...\", \"mistranslations\": [ ... ]}.",
        "Each mistranslation must include: original span, translated span that appears verbatim in the washed draft, severity (low/high), a concise reason, a constructive suggestion, and startIndex/endIndex (0-based) covering the translated span.",
        "Be stricter than a normal proofreader. Long drafts should surface multiple actionable items, including low-severity style or tone warnings when they sound softer, vaguer, or less technical than the original.",
        "If there are few factual mistakes, still surface low-severity issues for weak technical wording, diluted outcomes, generic expressions, or meaning drift."
    })
    @UserMessage("Original AI draft: {{original}}\nWashed Korean draft: {{washed}}\nContextual notes: {{context}}\nTarget length: {{minTarget}}~{{maxLength}} characters\nMinimum issue target: {{findingTarget}}\n\nAnalyze the washed draft and find the most critical mistranslations, meaning shifts, or overly soft phrasing. Aim to return at least {{findingTarget}} items unless the draft is exceptionally clean. Prefer low-severity warnings over missing obvious wording drift. For each issue:\n- cite the exact original phrase, the exact washed phrase, and explain how the meaning deviates or the tone weakens\n- provide a precise suggestion that keeps the IT/engineering voice\n- set severity to high when it causes factual error or technical ambiguity, otherwise low\n- include startIndex/endIndex for the washed phrase to help highlight it in the UI\nReturn the json request without markdown fences. Draft your humanPatchedText so it keeps the engineer's structure but fixes the top 2 risks.")
    DraftAnalysisResult analyzePatch(
            @V("original") String original,
            @V("washed") String washed,
            @V("maxLength") int maxLength,
            @V("minTarget") int minTarget,
            @V("findingTarget") int findingTarget,
            @V("context") String context);
}
