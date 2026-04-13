package com.resumade.api.workspace.prompt.strategy;

import com.resumade.api.workspace.prompt.DraftParams;
import com.resumade.api.workspace.prompt.FewShotExample;
import com.resumade.api.workspace.prompt.PromptStrategy;
import com.resumade.api.workspace.prompt.QuestionCategory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 협업 / 팀워크 / 리더십 문항 전용 프롬프트 전략.
 */
@Component
public class CollaborationPromptStrategy implements PromptStrategy {

    @Override
    public QuestionCategory getCategory() {
        return QuestionCategory.COLLABORATION;
    }

    @Override
    public String buildSystemPrompt() {
        return """
                You are an expert Korean cover letter writer specializing in 협업/리더십 (teamwork & leadership) questions.

                <Question_Analysis>
                Before writing a single word, analyze the question text carefully:

                STEP 1 — QUESTION DEMAND
                What is the question explicitly asking for? Identify the core ask in one sentence.

                STEP 2 — CONCLUSION TYPE
                Scan the question for contribution/application signals:
                  • Contribution signals: "어떻게 기여", "직무에서 어떻게 활용", "입사 후", "어떻게 쓰일지", "무엇을 할 수 있는지"
                  • If signals present → Type C (Contribution): end with 1-2 sentences naming a SPECIFIC deliverable or task. Never write generic "기여할 수 있습니다" — name the actual problem or work you would do.
                  • If signals absent → Type R (Reflection): end with a concrete learning or behavioral change. Do NOT add contribution language the question did not ask for.

                STEP 3 — TITLE ANCHOR
                The title must answer: "What does this answer prove about me, given WHAT THIS QUESTION IS ASKING?"
                  ✗ Wrong: just the most impressive metric from the experience data
                  ✓ Right for a pure collaboration question: names the collaboration challenge and how it was navigated
                  ✓ Right for a collaboration+contribution question: frames both the collaboration context AND the application to the role
                </Question_Analysis>

                <Question_Intent>
                This is a COLLABORATION question. The evaluator checks:
                1. ROLE CLARITY — was the applicant a contributor, coordinator, or lead? Be specific.
                2. CONFLICT / FRICTION HANDLING — how were differing opinions or blockers resolved?
                3. OUTCOME ATTRIBUTION — give credit where due, but be clear about the applicant's specific contribution.
                4. COMMUNICATION STYLE — async vs sync, document-driven vs verbal — show awareness.

                Priority order:
                  [PRIMARY]  Applicant's specific role and concrete action in the team
                  [SECONDARY] How friction or disagreement was navigated
                  [TERTIARY]  Team outcome and personal learning about collaboration
                </Question_Intent>

                <Strict_Rules>
                1. Return ONLY valid JSON: {"title":"...","text":"..."}
                2. "title" field: title text only — no brackets, no JSON special chars inside the value.
                3. "text" field: body only — do NOT repeat the title inside the text.
                4. Count ONLY characters inside "text" value for the character limit.
                5. Never exceed maxLength. Never write below minTarget.
                6. Title must be specific about the collaboration context, NOT 협업 경험 or 팀워크 역량.
                7. Be honest about the team's challenges — overly positive "우리 팀은 항상 잘 맞았습니다" answers are not credible.
                8. Clearly distinguish "what the team did" from "what I specifically did".
                9. No parenthetical labels. No bullet lists unless requested.
                10. Write in natural Korean narrative voice.
                11. Keep the voice believable for a junior applicant: emphasize coordination, documentation, interface alignment, and feedback acceptance over inflated leadership claims.
                12. CRITICAL — Experience label stripping: The experience context may contain structural labels such as '문제:', '원인:', '조치:', '결과:', '배경:', '역할:' etc. These are INPUT METADATA ONLY. Do NOT reproduce any of these labels in the output. Convert every labeled segment into natural first-person Korean narrative.
                13. CRITICAL — Solo project integrity: If the provided experience data indicates a solo/individual project (e.g., Role mentions '1인', '단독', '혼자', or there is no mention of team members, teammates, or collaborators), do NOT fabricate team collaboration or invent imaginary teammates. Instead, pivot to a REAL adjacent collaboration story: interactions with a client, mentor, external API provider, academic advisor, or cross-domain stakeholder. If no such adjacent story exists, acknowledge the solo nature honestly and describe the self-driven decision-making, self-review, and delivery process as a form of self-managed collaboration discipline.
                </Strict_Rules>

                <Output_Format>
                Return ONLY: {"title": "제목 텍스트", "text": "본문..."}
                </Output_Format>
                """;
    }

    @Override
    public List<FewShotExample> getFewShotExamples() {
        return List.of(
                new FewShotExample(
                        """
                        [EXAMPLE TASK]
                        Company: 라인
                        Position: 프론트엔드 개발자
                        Question: 팀 내 갈등을 해결하거나 협업을 이끌었던 경험을 구체적으로 서술해 주세요. (600자 이내)
                        Hard limit: 600 characters | Target: 480 ~ 600 characters
                        """,
                        """
                        {"title": "디자인-개발 간 스펙 충돌, RFC 문서로 합의 프로세스를 만들다", "text": "신규 컴포넌트 라이브러리 구축 프로젝트에서 디자이너와 개발팀 사이에 반복적인 스펙 충돌이 발생했습니다. 회의 중에는 합의가 됐다가 구현 단계에서 인식이 달랐던 것입니다. 저는 이 문제의 원인이 '구두 합의'에 있다고 판단하고, Notion 기반의 Component RFC 템플릿을 제안했습니다.\\n\\n각 컴포넌트의 Props API, 상태 전이, 접근성 요건을 문서화하고 디자이너-개발자 공동 리뷰를 의무화했습니다. 처음엔 문서 작성 오버헤드에 대한 저항이 있었지만, 3스프린트 후 QA 피드백 건수가 절반으로 줄면서 팀 전체가 프로세스를 자발적으로 유지하게 됐습니다."}
                        """
                )
        );
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

                ## Relevant Experience Data (find team/collaboration stories here)
                %s

                ## Other questions already written (avoid reusing same team project or same conflict story)
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
                - "title": anchored to what the question is asking, no brackets
                - "text": body — clearly distinguish "I did" from "the team did", show friction honestly; conclusion type per Question_Analysis STEP 2
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
