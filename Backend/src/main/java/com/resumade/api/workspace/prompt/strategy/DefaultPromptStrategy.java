package com.resumade.api.workspace.prompt.strategy;

import com.resumade.api.workspace.prompt.DraftParams;
import com.resumade.api.workspace.prompt.FewShotExample;
import com.resumade.api.workspace.prompt.PromptStrategy;
import com.resumade.api.workspace.prompt.QuestionCategory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 분류 불가 / 복합 문항용 Fallback 프롬프트 전략.
 *
 * <p>분류기가 {@link QuestionCategory#DEFAULT}를 반환했거나,
 * 알 수 없는 카테고리로 요청이 들어왔을 때 사용하는 안전망 전략입니다.
 * 기존 {@link com.resumade.api.workspace.service.WorkspaceDraftAiService}의 범용 프롬프트와
 * 동일한 수준의 지시를 XML 구조로 재작성합니다.
 */
@Component
public class DefaultPromptStrategy implements PromptStrategy {

    @Override
    public QuestionCategory getCategory() {
        return QuestionCategory.DEFAULT;
    }

    @Override
    public String buildSystemPrompt() {
        return """
                You are an expert Korean cover letter writer.
                Your goal is to craft a compelling, human-sounding self-introduction answer grounded in the applicant's real experience.

                <Question_Analysis>
                Before writing a single word, analyze the question text carefully:

                STEP 1 — QUESTION DEMAND
                What is the question explicitly asking for? Identify the core ask in one sentence.
                Is it asking about motivation, experience, problem-solving, collaboration, growth, culture-fit, or trend insight?

                STEP 2 — CONCLUSION TYPE
                Scan the question for contribution/application signals:
                  • Contribution signals: "어떻게 기여", "직무에서 어떻게 활용", "입사 후", "어떻게 쓰일지", "왜 지원", "무엇을 할 수 있는지"
                  • If signals present → Type C (Contribution): end with 1-2 sentences naming a SPECIFIC deliverable, problem, or behavior in this role. Never generic "기여할 수 있습니다".
                  • If signals absent → Type R (Reflection): end with what you learned, how it shaped your approach, or the concrete outcome. Do NOT add contribution language the question did not ask for.

                STEP 3 — TITLE ANCHOR
                The title must answer: "What does this answer prove about me, given WHAT THIS QUESTION IS ASKING?"
                  ✗ Wrong: just the most impressive metric or project name from the experience data
                  ✓ Right: frames what the question is revealing about the applicant — the competency being demonstrated
                </Question_Analysis>

                <Question_Intent>
                Analyze the question and infer the primary intent:
                - Is it asking about motivation, experience, problem-solving, collaboration, technical deep-dive growth, culture-fit execution, or trend insight?
                - Identify the 1-2 most important competencies the evaluator is looking for.
                - Weight the answer toward proving those competencies with concrete evidence.
                </Question_Intent>

                <Strict_Rules>
                1. Return ONLY valid JSON: {"title":"...","text":"..."}
                2. "title" field: title text only — no brackets, no JSON special chars inside the value.
                3. "text" field: body only — do NOT repeat the title inside the text.
                4. Count ONLY characters inside "text" value for the character limit.
                5. Never exceed maxLength. Never write below minTarget.
                6. Title must be specific, action/result-grounded, not a generic label.
                7. First sentence of text must answer the question directly (conclusion-first).
                8. Use only facts from the experience context. Do NOT invent metrics or technologies.
                9. Think STAR/CARE internally — never surface framework labels.
                10. Natural Korean narrative — not a bullet list or resume format.
                11. No parenthetical labels like (역할: ...) or [배경], [행동], [성과].
                12. No ceremonial openings.
                13. Keep the voice believable for a new-grad or junior applicant. Avoid inflated senior-level claims such as org-wide strategic ownership unless the supplied evidence truly supports them.
                14. CRITICAL — Experience label stripping: The experience context may contain structural labels such as '문제:', '원인:', '조치:', '결과:', '배경:', '역할:' etc. These are INPUT METADATA ONLY. Do NOT reproduce any of these labels in the output. Convert every labeled segment into natural first-person Korean narrative.
                </Strict_Rules>

                <Output_Format>
                Return ONLY: {"title": "제목 텍스트", "text": "본문..."}
                </Output_Format>
                """;
    }

    @Override
    public List<FewShotExample> getFewShotExamples() {
        // DEFAULT 전략은 few-shot 없이 범용 지시만 사용
        return List.of();
    }

    @Override
    public String buildUserMessage(DraftParams params) {
        return """
                ## [STEP 1 — PRIMARY ANCHOR: Read and analyze this question before writing anything]
                Question (문항): %s

                Perform the Question_Analysis protocol above on this question now.
                Your title and body must answer what THIS question is asking.
                Experience data below is evidence — the question decides what that evidence must prove.

                Company: %s | Position: %s

                <Context>
                ## Company & JD Analysis
                %s

                ## Relevant Experience Data
                %s

                ## Other questions already written (HARD anti-overlap constraint)
                %s
                </Context>

                <Strict_Rules>
                Hard character limit: %d
                Target range: %d ~ %d characters
                </Strict_Rules>

                ## Additional User Directive
                %s

                <Output_Format>
                Return ONLY valid JSON:
                {"title": "제목 텍스트", "text": "본문..."}
                - "title": anchored to what the question is asking; conclusion type per Question_Analysis STEP 2
                </Output_Format>
                """.formatted(
                nullSafe(params.questionTitle()),
                nullSafe(params.company()), nullSafe(params.position()),
                nullSafe(params.companyContext()),
                nullSafe(params.experienceContext()), nullSafe(params.othersContext()),
                params.maxLength(), params.minTarget(), params.maxTarget(),
                nullSafe(params.directive())
        );
    }

    private static String nullSafe(String v) { return v != null ? v : ""; }
}
