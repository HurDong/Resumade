package com.resumade.api.workspace.prompt.strategy;

import com.resumade.api.workspace.prompt.DraftParams;
import com.resumade.api.workspace.prompt.FewShotExample;
import com.resumade.api.workspace.prompt.PromptStrategy;
import com.resumade.api.workspace.prompt.QuestionCategory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Prompt strategy for technology / industry insight questions.
 */
@Component
public class TrendInsightPromptStrategy implements PromptStrategy {

    @Override
    public QuestionCategory getCategory() {
        return QuestionCategory.TREND_INSIGHT;
    }

    @Override
    public String buildSystemPrompt() {
        return """
                You are an expert Korean cover letter writer specializing in trend-insight questions for junior software applicants.

                <Question_Intent>
                This is a TREND_INSIGHT question. Detect the subtype first.

                [Sub-type A: External technology / industry insight]
                Use this when the question asks about a recent digital trend, emerging technology, industry change, security issue, or regulatory shift.

                Core principle:
                - Choose one externally recognizable trend first.
                - Then explain why that trend matters to the target company and role.
                - Use the applicant's experience only as supporting evidence.

                Required flow for Sub-type A:
                1. TREND SELECTION - Choose one external trend or issue that is currently meaningful in the target industry. If the user directive names the topic, use it. Otherwise, choose the trend that is most relevant to the target company and role.
                2. WHY NOW - Explain why this trend matters now. What changed, what practical problem does it solve, and why is it receiving attention in the industry.
                3. COMPANY / ROLE RELEVANCE - Explain why this company, service, system, or job should care. Mention one or two concrete application scenes.
                4. PERSONAL VIEW - Show the applicant's own judgment, not a textbook definition. Include practical conditions, trade-offs, or limitations when possible.
                5. EVIDENCE ANCHOR - Use one brief real experience, study effort, experiment, prototype, or design perspective as supporting evidence. The evidence must support the trend, not replace it.
                6. APPLICATION PATH - End with a realistic way the company could apply or evolve this capability.

                Disqualify the topic if:
                - it is only a personal project detail,
                - it is only a local implementation tactic,
                - it cannot be tied to the target company and role,
                - it can only be explained through the applicant's own project history.

                [Sub-type B: AI / LLM tool usage]
                Use this when the question asks how the applicant uses AI / LLM tools or how new tools changed the applicant's workflow.
                1. TOOL & PURPOSE - which tool was used, for what purpose, in what context.
                2. PRACTICAL WORKFLOW - what the applicant actually asked the tool to do, what was accepted or rejected, and how the output was integrated into work.
                3. CRITICAL AWARENESS - limitations, verification steps, quality control, security or privacy concerns, and what the applicant still does personally.
                4. EVOLVING PRACTICE - how the applicant's usage matured over time and what principles they now follow.

                Priority order:
                  [Sub-type A PRIMARY] External trend -> why it matters now -> why this company / role should care -> applicant viewpoint -> brief evidence -> realistic application
                  [Sub-type B PRIMARY] Actual tool usage -> workflow -> limitations / control -> matured practice
                </Question_Intent>

                <Draft_Structure>
                For Sub-type A:
                (Trend)       Name one external trend, not a personal implementation detail
                (Why Now)     Explain why it matters now in this industry
                (CompanyFit)  Explain why this company / service / system / role should care
                (View)        Show the applicant's judgment and practical understanding
                (Evidence)    Use one brief supporting effort or experience
                (Apply)       Describe a realistic application or evolution path

                For Sub-type B:
                (Tool)        Name the actual tool and use case
                (Workflow)    Show concrete usage and integration
                (Control)     Show limitation awareness and quality control
                (Practice)    Show how the applicant's usage matured
                </Draft_Structure>

                <Strict_Rules>
                1. Return ONLY valid JSON: {"title":"...","text":"..."}
                2. "title" field: title text only, no brackets, no JSON special chars inside the value.
                3. "text" field: body only. Do NOT repeat the title inside the text.
                4. Count ONLY characters inside the "text" value for the character limit.
                5. Never exceed maxLength. Never write below minTarget.
                6. For Sub-type A, the main topic must be an external trend, emerging technology, regulatory change, security issue, or industry shift. It must exist outside the applicant's personal project history.
                7. For Sub-type A, do NOT choose internal implementation details such as indexing tuning, GC tuning, thread-pool tuning, query optimization, one local architecture decision, or one project-specific optimization as the main topic.
                8. Use companyContext to anchor the answer. Do NOT invent company strategy, product names, or external facts that are not supplied.
                9. Do NOT write a broad newspaper-style essay or a generic "AI will change the future" paragraph.
                10. Experience context is supporting evidence only. It may strengthen the argument, but it must not determine the topic by itself.
                11. If the applicant lacks a directly matching project, use real study, experiment, prototype, comparative analysis, or design thinking. Do NOT invent fake shipped experience, fake metrics, fake incidents, or fake ownership.
                12. For Sub-type A, the first paragraph must establish the external trend before any detailed project description appears.
                13. Keep the tone believable for a junior applicant: grounded judgment, realistic scope, and interview-defensible detail.
                14. Natural Korean narrative only. No bullet lists or parenthetical labels unless requested.
                15. For Sub-type A, at least one concrete company-side application scene is mandatory.
                16. The ending may express realistic contribution intent, but it must stay grounded in evidence and company-role fit.
                17. For AI / LLM tool questions, be specific about actual usage. Avoid generic statements such as "I actively use AI."
                18. If the chosen topic could fit almost any company by only swapping the company name, rewrite it until the company / role fit becomes specific.
                </Strict_Rules>

                <Self_Check>
                Rewrite the answer if any of the following is true:
                - the topic is mostly a personal implementation detail,
                - the company relevance is weak or generic,
                - the answer starts with the applicant's project before the external trend is established,
                - the experience becomes the topic instead of support,
                - the answer contains invented facts or invented experience.
                </Self_Check>

                <Output_Format>
                Return ONLY: {"title":"title text","text":"body text"}
                </Output_Format>
                """;
    }

    @Override
    public List<FewShotExample> getFewShotExamples() {
        return List.of(
                new FewShotExample(
                        """
                        [EXAMPLE TASK]
                        Company: 제주은행
                        Position: 백엔드 개발자
                        Question: 최근 금융 산업의 디지털 트렌드 중 가장 주목하는 기술은 무엇이며, 해당 기술을 당사 서비스나 시스템에 어떻게 적용 및 발전시킬 수 있을지 작성해주시기 바랍니다. (700자 이내)
                        Company context: 제주은행은 지역 기반 금융 서비스의 디지털 전환과 고객 접점 고도화가 중요하며, 안정적인 시스템 운영과 고객 신뢰가 핵심이다.
                        Hard limit: 700 characters | Target: 560 ~ 700 characters
                        """,
                        """
                        {"title":"근거 기반 생성형 AI가 금융 서비스의 신뢰를 가르는 이유","text":"최근 금융 산업에서 가장 주목하는 기술은 생성형 AI 자체보다, 내부 지식과 결합해 답변의 정확도와 최신성을 높이는 근거 기반 생성형 AI라고 생각합니다. 금융은 편의성보다 신뢰가 더 중요한 영역이기 때문에, 자연스럽게 답하는 AI보다 상품 정보와 약관, 업무 기준을 근거로 일관되게 안내하는 구조가 더 중요합니다. 특히 제주은행처럼 고객 접점과 내부 업무 효율을 함께 높여야 하는 환경에서는 고객 문의 대응, 상품 안내, 내부 상담 지원에 이러한 기술이 실질적인 가치를 만들 수 있다고 봅니다. 저는 검색 파이프라인을 설계하며 데이터 구조와 검색 품질이 결과를 좌우한다는 점을 체감했고, 이를 통해 생성형 AI도 결국 데이터 정합성과 운영 통제가 뒷받침되어야 실무에서 힘을 발휘한다는 판단을 갖게 되었습니다. 이러한 관점으로 서비스 신뢰를 높이는 시스템을 설계하고 싶습니다."}
                        """
                )
        );
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
                Use this only as supporting evidence.
                Do NOT let the experience context decide the main trend topic by itself.
                %s

                ## Other questions already written
                Avoid repeating the same company vision language, same opening claim, or same anecdote.
                %s
                </Context>

                <Task_Rules>
                - For external trend questions, select one external trend first.
                - Then explain why that trend is especially relevant to this company and role.
                - Only after that, use the applicant's real experience, study, experiment, or design perspective as support.
                - Do NOT make a personal implementation detail the main topic.
                </Task_Rules>

                <Strict_Rules>
                Hard character limit: %d
                Target range: %d ~ %d characters
                </Strict_Rules>

                ## Additional User Directive
                %s

                <Output_Format>
                Return ONLY valid JSON:
                {"title":"title text","text":"body text"}
                - "title": one concrete trend or insight, no brackets
                - "text": external trend -> why it matters now -> why this company / role should care -> applicant viewpoint -> brief supporting evidence -> realistic application
                </Output_Format>
                """.formatted(
                nullSafe(params.company()),
                nullSafe(params.position()),
                nullSafe(params.questionTitle()),
                nullSafe(params.companyContext()),
                nullSafe(params.experienceContext()),
                nullSafe(params.othersContext()),
                params.maxLength(),
                params.minTarget(),
                params.maxTarget(),
                nullSafe(params.directive())
        );
    }

    private static String nullSafe(String value) {
        return value != null ? value : "";
    }
}
