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

                <Question_Intent>
                Analyze the question and infer the primary intent:
                - Is it asking about motivation, experience, problem-solving, collaboration, technical deep-dive growth, culture-fit execution, or trend insight?
                - Identify the 1-2 most important competencies the evaluator is looking for.
                - Weight the answer toward proving those competencies with concrete evidence.
                </Question_Intent>

                <Strict_Rules>
                1. Return ONLY valid JSON: {"text":"..."}
                2. Count ONLY characters inside "text" value.
                3. Never exceed maxLength. Never write below minTarget.
                4. Start with [제목] — specific, action/result-grounded, not a generic label.
                5. First sentence must answer the question directly (conclusion-first).
                6. Use only facts from the experience context. Do NOT invent metrics or technologies.
                7. Think STAR/CARE internally — never surface framework labels.
                8. Natural Korean narrative — not a bullet list or resume format.
                9. No parenthetical labels like (역할: ...) or [배경], [행동], [성과].
                10. No ceremonial openings.
                11. Keep the voice believable for a new-grad or junior applicant. Avoid inflated senior-level claims such as org-wide strategic ownership unless the supplied evidence truly supports them.
                </Strict_Rules>

                <Output_Format>
                Return ONLY: {"text": "[제목]\\n\\n본문..."}
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
                Company: %s
                Position: %s
                Question: %s

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
                Return ONLY valid JSON: {"text": "[제목]\\n\\n본문..."}
                </Output_Format>
                """.formatted(
                nullSafe(params.company()), nullSafe(params.position()),
                nullSafe(params.questionTitle()), nullSafe(params.companyContext()),
                nullSafe(params.experienceContext()), nullSafe(params.othersContext()),
                params.maxLength(), params.minTarget(), params.maxTarget(),
                nullSafe(params.directive())
        );
    }

    private static String nullSafe(String v) { return v != null ? v : ""; }
}
