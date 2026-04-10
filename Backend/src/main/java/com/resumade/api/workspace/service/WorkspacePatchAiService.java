package com.resumade.api.workspace.service;

import com.resumade.api.workspace.dto.DraftAnalysisResult;
import com.resumade.api.workspace.dto.SentencePairAnalysisResult;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface WorkspacePatchAiService {

    // ── 전체 텍스트 단일 용어 오역 탐지 ────────────────────────────────────────
    @SystemMessage({
            "You are a Korean IT-resume translation checker. Your task: compare an original Korean draft against a translation-washed version (KO→EN→KO machine translation) and detect where specific IT terms were lost or altered.",
            "",
            "Detect ONLY these three issue types:",
            "  1. TERM_WEAKENED — A romanized English IT term in the original was replaced by a generic Korean description in the washed version.",
            "     Good examples to flag: '헬스체크'→'상태 점검', '큐'→'대기열', '트랜잭션'→'거래', 'Redis'→'캐시 서버', 'API'→'인터페이스', '배치'→'일괄 처리'",
            "     Do NOT flag if the original term itself is already a plain Korean word (e.g., '가용성', '지연', '모듈').",
            "  2. PROPER_NOUN_CHANGED — A company name, project name, or product name was altered.",
            "     Examples: 'KB라이프'→'KB생명', 'SK하이닉스'→'SK', 'RESUMADE'→'리주메이드'",
            "  3. METRIC_DROPPED — A specific numeric metric was replaced by a vague expression.",
            "     Examples: '99.9%'→'높은', '3초'→'빠른 속도', '월 1,000만 건'→'대규모'",
            "",
            "For EACH candidate pair, apply this 4-step check before reporting:",
            "  Step 1. Find the `original` term verbatim in the Original text. If not found → skip.",
            "  Step 2. Find the `translated` term verbatim in the Washed text. If not found → skip.",
            "  Step 3. Confirm the `original` term does NOT appear verbatim anywhere in the Washed text.",
            "           If it does appear in the Washed text → the term was preserved, not mistranslated → skip.",
            "  Step 4. Confirm the change fits one of the 3 issue types above. If not → skip.",
            "",
            "Only report pairs that pass all 4 steps.",
            "Do NOT flag: tone changes, passive voice, sentence restructuring, spacing differences, honorifics.",
            "If nothing passes all 4 steps, return {\"mistranslations\": []}.",
            "Return ONLY valid JSON. No markdown."
    })
    @UserMessage("""
            [Original text]
            {{original}}

            [Washed text]
            {{washed}}

            Apply the 4-step check and return JSON only:
            {"mistranslations": [{"original": "...", "translated": "...", "issueType": "...", "reason": "...", "suggestion": "..."}]}
            """)
    SentencePairAnalysisResult analyzeSentencePair(
            @V("original") String original,
            @V("washed") String washed
    );



    @SystemMessage({
            "You are a specialized Technical Translation Reviewer for IT/Developer job descriptions and self-introductions in Korean.",
            "Your PRIMARY GOAL is to detect and fix mistranslated Technical Terms, Proper Nouns, suspicious translation patterns, loss of technical nuance, and passive voice that feels too mechanical.",
            "Return JSON that strictly matches the expected schema:",
            "{\"mistranslations\": [...], \"aiReviewReport\": {\"summary\": \"...\"}, \"humanPatchedText\": \"...\"}",
            "Categorize each issue into a 'severity' level: 'CRITICAL' or 'WARNING'.",
            "'CRITICAL' MUST be used for:",
            "- IT or Developer technical jargon that was translated into generic, non-technical words.",
            "  Example: '트랜잭션' translated as '거래' — CRITICAL. '레포지터리' as '저장소' — CRITICAL.",
            "  Example: 'API' mistranslated or dropped, 'Redis' described as '캐시 시스템' without the name — CRITICAL.",
            "- English frameworks, library names, or architecture components that were awkwardly translated to Korean.",
            "- Proper nouns (company names, university names, project names) that were altered or mistranslated.",
            "  Example: 'KB라이프' written as 'KB생명' — CRITICAL. 'SK하이닉스' written as 'SK' — CRITICAL.",
            "  Example: project name '리주마드' changed to '리주매드' or dropped entirely — CRITICAL.",
            "- Crucial numeric metrics and results that were dropped or generalized.",
            "'WARNING' MUST be used for:",
            "- Passive/weakened action verbs where strong contribution became modest or vague.",
            "- Translation tone awkwardness or unnatural literal syntax.",
            "- Important descriptive keywords or emphasis that were dropped from the original draft.",
            "- Mechanical '다나까' phrasing (did this, did that) that lacks a natural narrative flow.",
            "Each mistranslation item must include: id, issueType, original, originalSentence, translated, translatedSentence, severity, reason, suggestion, suggestedSentence.",
            "Allowed issueType values: TERM_WEAKENED, FRAMEWORK_MISTRANSLATED, PROPER_NOUN_CHANGED, METRIC_DROPPED, CONTRIBUTION_WEAKENED, AWKWARD_LITERAL, KEYWORD_DROPPED, MECHANICAL_TONE.",
            "CRITICAL FIELD RULE for 'translated': This field MUST be the MINIMUM verbatim substring found in the Washed Korean draft — prefer a single term or at most 3–5 words. NEVER put an entire sentence or clause in 'translated'. The 'translatedSentence' field is for the full sentence context.",
            "CRITICAL FIELD RULE for 'original': Similarly, use the minimal problematic term from the Original AI draft, not a full sentence.",
            "CRITICAL FIELD RULE: Both 'translated' and 'original' must be exact substrings that can be found verbatim in the respective texts.",
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
            - Set 'translated' to the EXACT verbatim substring from the Washed Korean draft (minimal phrase, 1-5 words).
            - Set 'original' to the EXACT verbatim substring from the Original AI draft.

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
