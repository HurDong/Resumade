package com.resumade.api.workspace.prompt.strategy;

import com.resumade.api.workspace.prompt.DraftParams;
import com.resumade.api.workspace.prompt.FewShotExample;
import com.resumade.api.workspace.prompt.PromptStrategy;
import com.resumade.api.workspace.prompt.QuestionCategory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 기술/산업/사회 이슈 분석 문항 전용 프롬프트 전략.
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
                This is a TREND_INSIGHT question. This category covers TWO sub-types:

                [Sub-type A: 기술/산업/사회 이슈 견해]
                When the question asks about a recent tech/industry/social issue:
                1. ISSUE SELECTION — one specific issue, not a vague mega-topic. If the user specifies an issue in the directive, use that. Otherwise, select the issue most relevant to the target role and company.
                2. ISSUE EXPLANATION — why this is an issue NOW. What changed, what's at stake, what are the trade-offs.
                3. PERSONAL STANCE — the applicant's own opinion: how they think about it, what they're doing about it, how it has changed their perspective or practice.
                4. IMPLICIT COMPANY CONNECTION — the applicant's engagement with this issue should naturally suggest they'd be a good fit for the company's challenges. Do NOT write "기여하겠습니다" directly — let the connection emerge from the content itself.

                [Sub-type B: AI/LLM 도구 활용 경험]
                When the question asks about AI/LLM tool usage or how the applicant uses new technologies:
                1. TOOL & PURPOSE — which AI/LLM tool was used, for what purpose, in what context.
                2. PRACTICAL APPLICATION — concrete usage examples: what was the input, what was the output, how was it integrated into the workflow.
                3. CRITICAL AWARENESS — limitations encountered, quality control measures, what the applicant does vs. delegates to the tool.
                4. EVOLVING PRACTICE — how the applicant's usage has matured over time, and what principles they now follow.

                Priority order:
                  [PRIMARY]  Issue/tool definition → why it matters → personal stance and action
                  [SECONDARY] How this has changed the applicant's thinking or practice
                  [TERTIARY]  Implicit connection to the company and role (NOT explicit "기여하겠습니다")
                </Question_Intent>

                <Draft_Structure>
                (Issue)     이슈/도구 정의 — 구체적 이슈 하나 선정, 무엇이 변화했는지
                (Why)       왜 이슈인가 — 기술적/사회적 배경, 트레이드오프
                (Stance)    나의 견해와 행동 — 어떻게 생각하고, 실제로 어떻게 활용/대응하고 있는가
                (Change)    이를 통해 어떻게 변화했는가 — 사고방식이나 업무 방식의 구체적 변화
                (Implicit)  기업 연결 — 직접 "기여하겠습니다"가 아닌, 이 이슈에 대한 관심과 행동이 자연스럽게 해당 기업의 맥락으로 이어지도록
                </Draft_Structure>

                <Strict_Rules>
                1. Return ONLY valid JSON: {"title":"...","text":"..."}
                2. "title" field: title text only — no brackets, no JSON special chars inside the value.
                3. "text" field: body only — do NOT repeat the title inside the text.
                4. Count ONLY characters inside "text" value for the character limit.
                5. Never exceed maxLength. Never write below minTarget.
                6. Title must name the actual issue or insight, NOT 시사 이슈, 기술 동향, or 제 생각.
                7. Use companyContext to anchor the issue. Do NOT invent company strategy, product names, or external facts that are not supplied.
                8. Do NOT write a broad newspaper-style essay. The answer must stay anchored to the applicant's real stance and actions.
                9. If experience context contains a relevant technical episode, use it briefly as a credibility anchor rather than making the whole essay autobiographical.
                10. Keep the tone believable for a junior applicant: offer a grounded viewpoint and action-level insight, not executive-level certainty.
                11. Natural Korean narrative only. No bullet lists or parenthetical labels unless requested.
                12. CRITICAL: Express the desire to contribute IMPLICITLY, not explicitly. Do NOT write "기여하겠습니다", "도움이 되고 싶습니다". Instead, let the content — the applicant's interest, actions, and perspective — naturally suggest alignment with the company.
                13. For LLM/AI tool questions: be specific about actual usage (tool name, use case, workflow integration). Avoid generic "AI 시대에 적응" type statements.
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
                        Company: 삼성SDS
                        Position: 클라우드 개발자
                        Question: 최근 IT 이슈 중 중요하다고 생각하는 한 가지와, 그 이유를 당사와 연결해 설명해 주세요. (700자 이내)
                        Company context: 삼성SDS는 생성형 AI 서비스와 클라우드 MSP 사업을 함께 확장하고 있으며, 엔터프라이즈 고객의 보안과 운영 안정성을 강하게 요구받고 있음.
                        Hard limit: 700 characters | Target: 560 ~ 700 characters
                        """,
                        """
                        {"title": "생성형 AI 확산 국면에서 더 중요해진 것은 성능보다 기업 데이터 거버넌스라고 생각합니다", "text": "최근 가장 중요한 IT 이슈는 생성형 AI 자체의 등장이 아니라, 이를 실제 기업 환경에 안전하게 적용할 수 있는 데이터 거버넌스 체계라고 생각합니다. 일반 사용자 서비스에서는 빠른 기능 출시가 경쟁력이 될 수 있지만, 엔터프라이즈 영역에서는 부정확한 답변이나 민감 정보 노출이 곧 신뢰 하락으로 이어질 수 있기 때문입니다.\\n\\n삼성SDS가 생성형 AI 서비스와 클라우드 사업을 함께 확장하는 상황에서는 모델 성능 못지않게 접근 권한, 로그 추적성, 운영 정책이 핵심 경쟁력이 될 것이라고 봅니다. 저 역시 프로젝트에서 로그 적재 구조와 권한 분리를 설계하며 기능보다 운영 안정성이 더 중요한 순간을 경험했습니다. 입사 후에는 AI 기능을 빠르게 붙이는 개발자보다, 실제 고객 환경에서 안전하게 작동하는 구조를 고민하는 개발자로 기여하고 싶습니다."}
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

                ## Relevant Experience Data (use only if it gives a grounded technical angle for the issue)
                %s

                ## Other questions already written (avoid repeating the same company vision language or the same anecdote)
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
                - "title": names one concrete issue or insight, no brackets
                - "text": issue definition → why it matters to this company → reasoned viewpoint → contribution angle
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
