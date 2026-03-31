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
                This is a TREND_INSIGHT question. The evaluator looks for:
                1. ISSUE SELECTION — one specific technological, industrial, or social issue, not a vague mega-topic.
                2. COMPANY RELEVANCE — why this issue matters to the target company, product, or business direction now.
                3. REASONED VIEWPOINT — a clear opinion with technical or business logic, not a news summary.
                4. APPLICANT CREDIBILITY — a small but real hands-on angle that shows the opinion is grounded.

                Priority order:
                  [PRIMARY]  Define the issue clearly and connect it to the company context
                  [SECONDARY] Present a reasoned viewpoint with trade-offs or risks
                  [TERTIARY]  Bridge the issue to how the applicant wants to contribute in the role
                </Question_Intent>

                <Strict_Rules>
                1. Return ONLY valid JSON: {"text":"..."}
                2. Count ONLY characters inside "text" value.
                3. Never exceed maxLength. Never write below minTarget.
                4. [제목]: must name the actual issue or insight, NOT [시사 이슈], [기술 동향], or [제 생각].
                5. Use companyContext heavily. Do NOT invent company strategy, product names, or external facts that are not supplied.
                6. Do NOT write a broad newspaper-style essay. The answer must stay anchored to the company and role.
                7. If experience context contains a relevant technical episode, use it briefly as a credibility anchor rather than making the whole essay autobiographical.
                8. Keep the tone believable for a junior applicant: offer a grounded viewpoint and contribution angle, not executive-level certainty.
                9. Natural Korean narrative only. No bullet lists or parenthetical labels unless requested.
                </Strict_Rules>

                <Output_Format>
                Return ONLY: {"text": "[제목]\\n\\n본문..."}
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
                        {"text": "[생성형 AI 확산 국면에서 더 중요해진 것은 성능보다 기업 데이터 거버넌스라고 생각합니다]\\n\\n최근 가장 중요한 IT 이슈는 생성형 AI 자체의 등장이 아니라, 이를 실제 기업 환경에 안전하게 적용할 수 있는 데이터 거버넌스 체계라고 생각합니다. 일반 사용자 서비스에서는 빠른 기능 출시가 경쟁력이 될 수 있지만, 엔터프라이즈 영역에서는 부정확한 답변이나 민감 정보 노출이 곧 신뢰 하락으로 이어질 수 있기 때문입니다.\\n\\n삼성SDS가 생성형 AI 서비스와 클라우드 사업을 함께 확장하는 상황에서는 모델 성능 못지않게 접근 권한, 로그 추적성, 운영 정책이 핵심 경쟁력이 될 것이라고 봅니다. 저 역시 프로젝트에서 로그 적재 구조와 권한 분리를 설계하며 기능보다 운영 안정성이 더 중요한 순간을 경험했습니다. 입사 후에는 AI 기능을 빠르게 붙이는 개발자보다, 실제 고객 환경에서 안전하게 작동하는 구조를 고민하는 개발자로 기여하고 싶습니다."}
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
                {"text": "[제목]\\n\\n본문..."}
                - [제목]: names one concrete issue or insight
                - Structure: issue definition → why it matters to this company → reasoned viewpoint → contribution angle
                - Keep the answer grounded in company context, not a generic news commentary
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
