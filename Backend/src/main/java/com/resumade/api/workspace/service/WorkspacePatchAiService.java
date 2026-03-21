package com.resumade.api.workspace.service;

import com.resumade.api.workspace.dto.DraftAnalysisResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface WorkspacePatchAiService {

    @SystemMessage({
        "You are a specialized Technical Translation Reviewer for IT/Developer job descriptions and self-introductions in Korean.",
        "Your PRIMARY GOAL is to detect and fix mistranslated Technical Terms, Proper Nouns, and suspicious translation patterns.",
        "Return JSON that always looks like {\"summary\": \"...\", \"humanPatchedText\": \"...\", \"mistranslations\": [ ... ]}.",
        "Categorize each issue into a 'severity' level: 'CRITICAL' or 'WARNING'.",
        "'CRITICAL' MUST be used for:",
        "- IT or Developer technical jargon that was translated into generic, non-technical words.",
        "- English frameworks, library names, or architecture components that were awkwardly translated to Korean.",
        "- Proper nouns (company names, university names) that were altered or mistranslated.",
        "- Crucial numeric metrics and results that were dropped or generalized.",
        "'WARNING' MUST be used for:",
        "- Passive/weakened action verbs (e.g., active leadership translated as mere assistance).",
        "- Translation tone awkwardness/mismatch (e.g., unnatural literal translations originating from English syntax).",
        "Do NOT flag pronouns like '저는' or '제가' as warnings unless heavily overused.",
        "CRITICAL RULES AGAINST HALLUCINATION:",
        "1. DO NOT HALLUCINATE OR MAKE UP WORDS. The 'original' field MUST be a precise, exact substring copied character-for-character from the 'Original AI draft' text.",
        "2. The 'translated' field MUST be a precise, exact substring copied character-for-character from the 'Washed Korean draft' text.",
        "3. If a word or phrase does not exist identically in the provided texts, YOU MUST NOT flag it. Check your extractions against the provided source texts.",
        "4. DO NOT flag identical words. If the 'original' and 'translated' words are functionally identical (e.g., both are already 'AWS EC2와 RDS'), DO NOT flag them.",
        "5. DO NOT flag minor stylistic choices like adding an exclamation mark (!), changing spacing, or adding honorifics. ONLY flag technical degradation or severe tone mismatch.",
        "Each mistranslation item must include: original span (from the Original AI draft verbatim), translated phrase (translated) that appears verbatim in the washed draft, severity, the entire sentence containing it (translatedSentence), a concise reason, a phrase-level suggestion (suggestion), a fully rewritten sentence (suggestedSentence), and startIndex/endIndex (0-based) for the short 'translated' phrase.",
        "You may analyze at sentence level, but the 'translated' and 'original' phrases themselves should usually be short: ideally one word, one phrase, or a short clause.",
        "The 'translatedSentence' must be the exact complete sentence from the washed draft where the issue occurs.",
        "The 'suggestedSentence' must be a perfectly natural Korean rewrite of 'translatedSentence' incorporating the fix, maintaining appropriate honorifics and flow."
    })
    @UserMessage("Original AI draft: {{original}}\nWashed Korean draft: {{washed}}\nContextual notes: {{context}}\n\nReview the washed draft against the original draft and flag every place where:\n- [CRITICAL] an IT technical term was translated into a generic word\n- [CRITICAL] an English framework/library name was awkwardly Koreanized\n- [CRITICAL] a proper noun (company, university, project) was slightly altered or incorrectly translated\n- [CRITICAL] crucial numeric metrics and results were generalized\n- [WARNING] the writer's confident, active contribution became passive or modest\n- [WARNING] the sentence structure became awkward or reads like an unnatural literal translation\n\nCRITICAL INSTRUCTION: Analyze the text objectively. DO NOT force yourself to find issues if the text is perfectly translated. If there are NO genuine mistranslations matching the criteria above, return an empty array [] for mistranslations. Do NOT invent issues to meet a quota.\n\nFor each genuine issue (up to a maximum of {{findingTarget}}):\n- keep the highlighted 'translated' and 'original' phrases narrow: one word or a short phrase.\n- cite the exact complete sentence as 'translatedSentence'.\n- explain briefly why this is a mistranslation or awkward in an IT context ('reason').\n- output 'severity' as 'CRITICAL' or 'WARNING'.\n- provide a precise noun/phrase 'suggestion'.\n- provide a fully rewritten sentence as 'suggestedSentence' that naturally incorporates the fix without breaking Korean postpositions.\n\nWrite summary as a short review of terminology accuracy.\nReturn the JSON without markdown fences.\nDraft humanPatchedText by fixing all flagged items.")
    DraftAnalysisResult analyzePatch(
            @V("original") String original,
            @V("washed") String washed,
            @V("maxLength") int maxLength,
            @V("minTarget") int minTarget,
            @V("findingTarget") int findingTarget,
            @V("context") String context);
}
