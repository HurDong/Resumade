package com.resumade.api.workspace.service;

import com.resumade.api.workspace.dto.DraftAnalysisResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface WorkspacePatchAiService {

    @SystemMessage({
            "You are a specialized Technical Translation Reviewer for IT/Developer job descriptions and self-introductions in Korean.",
            "Your PRIMARY GOAL is to detect and fix mistranslated Technical Terms, Proper Nouns, suspicious translation patterns, loss of technical nuance, and passive voice that feels too mechanical.",
            "Return JSON that strictly matches the expected schema:",
            "{\"mistranslations\": [...], \"aiReviewReport\": {\"summary\": \"...\", \"taggedOriginalText\": \"...\", \"taggedWashedText\": \"...\"}, \"humanPatchedText\": \"...\"}",
            "Categorize each issue into a 'severity' level: 'CRITICAL' or 'WARNING'.",
            "'CRITICAL' MUST be used for:",
            "- IT or Developer technical jargon that was translated into generic, non-technical words.",
            "- English frameworks, library names, or architecture components that were awkwardly translated to Korean.",
            "- Proper nouns (company names, university names, project names) that were altered or mistranslated.",
            "- Crucial numeric metrics and results that were dropped or generalized.",
            "'WARNING' MUST be used for:",
            "- Passive/weakened action verbs where strong contribution became modest or vague.",
            "- Translation tone awkwardness or unnatural literal syntax.",
            "- Important descriptive keywords or emphasis that were dropped from the original draft.",
            "- Mechanical '다나까' phrasing (did this, did that) that lacks a natural narrative flow.",
            "CRITICAL RULES FOR INLINE TAGGING:",
            "1. You must assign a unique 'id' (e.g., \"mis-1\", \"mis-2\") to each found mistranslation in the 'mistranslations' array.",
            "2. In the 'taggedOriginalText' field, return the ENTIRE Original AI draft exactly as it is, BUT wrap the problematic phrases with `<mark data-mis-id=\"mis-X\">problematic phrase</mark>`.",
            "3. In the 'taggedWashedText' field, return the ENTIRE Washed Korean draft exactly as it is, BUT wrap the corresponding anchor phrases with `<mark data-mis-id=\"mis-X\">anchor phrase</mark>`.",
            "4. DO NOT change any other text, whitespace, or paragraphs inside the tagged representations. Only insert the <mark> tags into the exact locations of the original texts.",
            "Each mistranslation item must include: id, issueType, original, originalSentence, translated, translatedSentence, severity, reason, suggestion, and suggestedSentence.",
            "Allowed issueType values: TERM_WEAKENED, FRAMEWORK_MISTRANSLATED, PROPER_NOUN_CHANGED, METRIC_DROPPED, CONTRIBUTION_WEAKENED, AWKWARD_LITERAL, KEYWORD_DROPPED, MECHANICAL_TONE.",
            "The 'suggestedSentence' must be a perfectly natural Korean rewrite of translatedSentence.",
            "IMPORTANT PRODUCT RULE: do not create a new final draft. 'humanPatchedText' must be exactly the same as the input Washed Korean draft except for whitespace normalization if absolutely necessary."
    })
    @UserMessage("""
            Original AI draft: {{original}}
            Washed Korean draft: {{washed}}
            Contextual notes: {{context}}

            Review the washed draft against the original draft and flag every place where:
            - [CRITICAL] an IT technical term was translated into a generic word
            - [CRITICAL] an English framework/library name was awkwardly Koreanized
            - [CRITICAL] a proper noun (company, university, project) was slightly altered
            - [CRITICAL] crucial numeric metrics and results were generalized or dropped
            - [WARNING] the writer's confident, active contribution became passive or modest
            - [WARNING] the sentence structure became awkward or reads like an unnatural literal translation
            - [WARNING] a meaningful keyword from the original draft was dropped
            - [WARNING] repetitive or mechanical phrasing like '개발했습니다.' that loses natural flow

            CRITICAL INSTRUCTION:
            You MUST find the top 1 to {{findingTarget}} most awkward, mechanical, or mistranslated parts.
            Do NOT return an empty array if there is even the slightest hint of machine-like passive translation or lost technical nuance. Be extremely strict and sensitive towards finding areas to improve human-like quality.

            For each genuine issue:
            - Assign a unique 'id' like "mis-1".
            - choose an issueType from the allowed values.
            - explain briefly why this is a mistranslation, omission, weakening, or awkward phrasing in an IT context.
            - output severity as CRITICAL or WARNING.
            - provide a precise noun or phrase suggestion, and a fully rewritten sentence as suggestedSentence.

            Create 'aiReviewReport.taggedOriginalText' and 'aiReviewReport.taggedWashedText' by taking the original texts and inserting `<mark data-mis-id="{id}">...</mark>` exactly where the flagged phrases are.
            Set 'humanPatchedText' to the same Washed Korean draft. Do not apply the fixes into a rewritten final text.
            Return the JSON without markdown fences.
            """)
    DraftAnalysisResult analyzePatch(
            @V("original") String original,
            @V("washed") String washed,
            @V("maxLength") int maxLength,
            @V("minTarget") int minTarget,
            @V("findingTarget") int findingTarget,
            @V("context") String context);
}
